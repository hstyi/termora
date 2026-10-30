"""隔离 SSH 测试服务；只监听回环地址，不执行命令，不读取用户 SSH 配置。"""

import logging
import os
import queue
import socket
import sys
import threading
import struct

import paramiko


class TestServer(paramiko.ServerInterface):
    """为单条连接提供密码、公钥、多因素认证和受控 Channel 行为。"""

    def __init__(self, user_key):
        """用 user_key 指定测试允许的公钥，所有连接状态均限定在本实例。"""
        self.user_key = user_key  # 测试专用公钥。
        self.publickey_done = False  # 当前连接的第一认证因素是否完成。
        self.round = 0  # keyboard-interactive 当前问题轮次。
        self.requests = queue.Queue()  # 将协议回调产生的 Channel 操作交给应用线程。
        self.sizes = {}  # Channel ID 对应的最近一次 PTY 网格。
        self.resizes = {}  # Shell 应用线程待输出的网格通知。
        self.direct = {}  # direct-tcpip Channel ID 对应目标。
        self.listeners = {}  # 远端转发监听器。
        self.transport = None  # 所属 SSH Transport，在启动服务前绑定。
        self.x11_cookie = None  # 客户端发来的替代 cookie，不能是本地真实 cookie。

    def check_channel_direct_tcpip_request(self, chanid, origin, destination):
        """接受 chanid 的 direct-tcpip，origin 仅用于协议元数据。"""
        if destination[0] == "reject.invalid":
            return paramiko.OPEN_FAILED_CONNECT_FAILED
        self.direct[chanid] = destination
        return paramiko.OPEN_SUCCEEDED

    def check_port_forward_request(self, address, port):
        """在 address:port 创建测试监听，零端口由系统分配。"""
        if address != "127.0.0.1":
            return False
        listener = socket.socket()
        try:
            listener.bind((address, port))
            listener.listen()
        except OSError:
            listener.close()
            return False
        bound = listener.getsockname()[1]
        self.listeners[(address, bound)] = listener
        threading.Thread(target=accept_remote, args=(self, listener, address, bound), daemon=True).start()
        return bound

    def cancel_port_forward_request(self, address, port):
        """撤销 address:port 的测试监听。"""
        listener = self.listeners.pop((address, port), None)
        if listener:
            try:
                listener.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            listener.close()

    def check_channel_x11_request(self, channel, single_connection, auth_protocol, auth_cookie, screen_number):
        """保存 channel 的 X11 替代认证信息，不使用本机 X Server。"""
        if auth_protocol != "MIT-MAGIC-COOKIE-1":
            return False
        self.x11_cookie = bytes.fromhex(auth_cookie.decode("ascii"))
        return True

    def check_channel_forward_agent_request(self, channel):
        """允许 channel 请求转发，测试通过模拟 Agent 验证转接。"""
        return True

    def get_allowed_auths(self, username):
        """按 username 和已完成因素返回服务器允许继续的方法。"""
        if username == "publickeyotp":
            return "keyboard-interactive" if self.publickey_done else "publickey"
        if username in ("otp", "zero"):
            return "keyboard-interactive"
        if username == "publickey":
            return "publickey"
        return "password"

    def check_auth_password(self, username, password):
        """只接受测试用户 username=password 和固定 password=secret。"""
        return (paramiko.AUTH_SUCCESSFUL if username == "password" and password == "secret"
                else paramiko.AUTH_FAILED)

    def check_auth_publickey(self, username, key):
        """校验 key，并为 username=publickeyotp 保留第一因素完成状态。"""
        if username not in ("publickey", "publickeyotp") or key != self.user_key:
            return paramiko.AUTH_FAILED
        self.publickey_done = True
        return paramiko.AUTH_PARTIALLY_SUCCESSFUL if username == "publickeyotp" else paramiko.AUTH_SUCCESSFUL

    def check_auth_interactive(self, username, submethods):
        """为 username 创建初始挑战；submethods 不影响本测试服务。"""
        self.round = 0
        if username == "zero":
            return paramiko.InteractiveQuery("Notice", "No input required")
        if username == "publickeyotp":
            if not self.publickey_done:
                return paramiko.AUTH_FAILED
            self.round = 1
            return paramiko.InteractiveQuery("OTP", "Enter second factor", ("Verification code:", False),
                                            ("Account label:", True))
        if username == "otp":
            return paramiko.InteractiveQuery("Login", "Enter password", ("Password:", False))
        return paramiko.AUTH_FAILED

    def check_auth_interactive_response(self, responses):
        """检查有序 responses，必要时返回下一轮 OTP 问题。"""
        if not responses:
            return paramiko.AUTH_SUCCESSFUL
        if self.round == 0 and responses == ["secret"]:
            self.round = 1
            return paramiko.InteractiveQuery("OTP", "Enter second factor", ("Verification code:", False),
                                            ("Account label:", True))
        return paramiko.AUTH_SUCCESSFUL if responses == ["123456", "work"] else paramiko.AUTH_FAILED

    def check_channel_request(self, kind, chanid):
        """仅允许 session 类型 kind，chanid 由 Paramiko 分配。"""
        return paramiko.OPEN_SUCCEEDED if kind == "session" else paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED

    def check_channel_exec_request(self, channel, command):
        """将 channel 的 command 交给受控处理器，reject 模式显式拒绝请求。"""
        if command == b"reject":
            return False
        self.requests.put((channel, command))
        return True

    def check_channel_pty_request(self, channel, term, width, height, pixelwidth, pixelheight, modes):
        """保存 channel 的 width/height；拒绝 term=reject-pty，其他终端参数不参与模拟。"""
        if term == b"reject-pty":
            return False
        self.sizes[channel.get_id()] = (width, height)
        self.resizes[channel.get_id()] = queue.Queue()
        return True

    def check_channel_window_change_request(self, channel, width, height, pixelwidth, pixelheight):
        """保存 channel 最新 width/height 并通知模拟 shell，像素尺寸忽略。"""
        self.sizes[channel.get_id()] = (width, height)
        self.resizes[channel.get_id()].put((width, height))
        return True

    def check_channel_shell_request(self, channel):
        """在 channel 上启动受控回显 shell，不启动本机进程。"""
        self.requests.put((channel, b"shell"))
        return True


def run_channel(server, channel, command):
    """按 command 模拟 server 所属 channel 的输出、背压和关闭行为。"""
    try:
        if command == b"disconnect":
            channel.get_transport().close()
            return
        if command in (b"agent", b"x11", b"x11-bad-cookie"):
            try:
                if command == b"agent":
                    forwarded = server.transport.open_forward_agent_channel()
                    payload = b"\0\0\0\1\x0b"
                else:
                    forwarded = server.transport.open_x11_channel(("127.0.0.1", 12345))
                    cookie = server.x11_cookie or b"z" * 16
                    if command == b"x11-bad-cookie":
                        cookie = b"z" * 16
                    payload = struct.pack("<BBHHHHH", ord("l"), 0, 11, 0, 18, 16, 0)
                    payload += b"MIT-MAGIC-COOKIE-1\0\0" + cookie + b"X11-DATA"
                forwarded.settimeout(5)
                forwarded.sendall(payload[:2])
                forwarded.sendall(payload[2:])
                response = forwarded.recv(4096)
                channel.sendall(response or b"FORWARD-REJECTED")
                forwarded.close()
            except (EOFError, OSError, paramiko.SSHException):
                channel.sendall(b"FORWARD-REJECTED")
            channel.send_exit_status(0)
        elif command == b"report":
            channel.sendall("stdout-你好\n".encode())
            channel.sendall_stderr(b"stderr-marker\n")
            channel.send_exit_status(7)
        elif command == b"signal":
            message = paramiko.Message()
            message.add_byte(bytes([paramiko.common.MSG_CHANNEL_REQUEST]))
            message.add_int(channel.remote_chanid)
            message.add_string("exit-signal")
            message.add_boolean(False)
            message.add_string("TERM")
            message.add_boolean(False)
            message.add_string("terminated")
            message.add_string("")
            channel.get_transport()._send_user_message(message)
        elif command == b"no-status":
            channel.sendall(b"tail-without-status")
        elif command == b"large":
            channel.sendall(b"x" * (6 * 1024 * 1024))
            channel.sendall_stderr(b"error-tail")
            channel.send_exit_status(0)
        elif command == b"block":
            # 不读取输入，真实 SSH 窗口耗尽后客户端必须让出 worker。
            channel.status_event.wait(30)
            return
        elif command in (b"shell", b"cat"):
            channel.settimeout(0.1)
            if command == b"shell":
                width, height = server.sizes[channel.get_id()]
                channel.sendall(f"SIZE={width},{height}\n".encode())
            while not channel.closed:
                if command == b"shell":
                    changes = server.resizes[channel.get_id()]
                    while not changes.empty():
                        width, height = changes.get_nowait()
                        channel.sendall(f"SIZE={width},{height}\n".encode())
                try:
                    data = channel.recv(65536)
                except socket.timeout:
                    continue
                if not data:
                    break
                channel.sendall(data)
            channel.sendall(b"EOF-TAIL")
            channel.send_exit_status(0)
        else:
            channel.sendall(command)
            channel.send_exit_status(0)
        channel.shutdown_write()
        channel.close()
    except (EOFError, OSError, paramiko.SSHException):
        pass


def run_connection(sock, host_key, user_key):
    """在 sock 上处理一条连接，使用显式 host_key 和 user_key。"""
    transport = paramiko.Transport(sock)
    server = TestServer(user_key)
    server.transport = transport
    channels = []  # Transport 只保留弱引用；应用持有所有已接受的 Channel。
    try:
        transport.add_server_key(host_key)
        transport.start_server(server=server)
        while transport.is_active():
            channel = transport.accept(0.01)
            if channel is not None:
                channels.append(channel)
                destination = server.direct.pop(channel.get_id(), None)
                if destination:
                    threading.Thread(target=connect_direct, args=(channel, destination), daemon=True).start()
            try:
                while True:
                    channel, command = server.requests.get_nowait()
                    threading.Thread(target=run_channel, args=(server, channel, command), daemon=True).start()
            except queue.Empty:
                pass
    except (EOFError, OSError, paramiko.SSHException):
        pass
    finally:
        for listener in list(server.listeners.values()):
            listener.close()
        transport.close()


def bridge(channel, sock):
    """在 channel 与 sock 之间转接，EOF 只关闭对应发送方向。"""
    def copy(source, destination, channel_target):
        """将 source 数据复制到 destination，channel_target 区分半关闭 API。"""
        try:
            while True:
                data = source.recv(65536)
                if not data:
                    break
                destination.sendall(data)
            if channel_target:
                destination.shutdown_write()
            else:
                destination.shutdown(socket.SHUT_WR)
        except (EOFError, OSError, paramiko.SSHException):
            pass
    worker = threading.Thread(target=copy, args=(sock, channel, True), daemon=True)
    worker.start()
    copy(channel, sock, False)
    worker.join(10)
    channel.close()
    sock.close()


def connect_direct(channel, destination):
    """将 direct-tcpip channel 连接到 destination 的回环测试目标。"""
    try:
        host, port = destination
        if host == "proxy-target.invalid":
            host = "127.0.0.1"
        if host not in ("127.0.0.1", "localhost", "::1"):
            channel.close()
            return
        sock = socket.create_connection((host, port), timeout=5)
        sock.settimeout(None)
        bridge(channel, sock)
    except (EOFError, OSError, paramiko.SSHException):
        channel.close()


def accept_remote(server, listener, address, port):
    """将 listener 接受的连接转换为 address:port 的 forwarded-tcpip。"""
    try:
        while server.transport.is_active():
            sock, origin = listener.accept()
            channel = server.transport.open_forwarded_tcpip_channel(origin, (address, port))
            threading.Thread(target=bridge, args=(channel, sock), daemon=True).start()
    except (EOFError, OSError, paramiko.SSHException):
        pass


def main(directory):
    """在 directory 创建隔离密钥并监听临时回环端口，将实际端口写到 stdout。"""
    logging.getLogger("paramiko").setLevel(logging.ERROR)
    host_key = paramiko.RSAKey.generate(2048)
    user_key = paramiko.RSAKey.generate(2048)
    user_key.write_private_key_file(os.path.join(directory, "user_key"), password="key-passphrase")
    with open(os.path.join(directory, "user_key.pub"), "w", encoding="ascii") as public_file:
        public_file.write(f"{user_key.get_name()} {user_key.get_base64()}\n")
    listener = socket.socket()
    listener.bind(("127.0.0.1", 0))
    listener.listen()
    print(listener.getsockname()[1], flush=True)
    while True:
        sock, _ = listener.accept()
        threading.Thread(target=run_connection, args=(sock, host_key, user_key), daemon=True).start()


if __name__ == "__main__":
    main(sys.argv[1])
