"""本地 HTTP CONNECT、SOCKS4a、SOCKS5 测试代理；不允许访问外部地址。"""

import base64
import socket
import struct
import sys
import threading


def exact(sock, count):
    """从 sock 读取恰好 count 字节，提前 EOF 时终止本条测试连接。"""
    result = b""
    while len(result) < count:
        part = sock.recv(count - len(result))
        if not part:
            raise EOFError()
        result += part
    return result


def terminated(sock, limit=1024):
    """从 sock 读取 NUL 结束字段，限制为 limit 字节。"""
    result = b""
    while len(result) <= limit:
        value = exact(sock, 1)
        if value == b"\0":
            return result
        result += value
    raise ValueError("field too long")


def copy(source, destination):
    """双向 relay 中的一侧，source EOF 只关闭 destination 的发送方向。"""
    try:
        while True:
            data = source.recv(65536)
            if not data:
                break
            destination.sendall(data)
        destination.shutdown(socket.SHUT_WR)
    except OSError:
        pass


def serve(sock, protocol, authenticated):
    """协商 sock 的 protocol；authenticated 要求固定测试凭据。"""
    upstream = None
    try:
        sock.settimeout(5)
        if protocol == "http":
            header = b""
            while not header.endswith(b"\r\n\r\n") and len(header) < 16384:
                header += exact(sock, 1)
            token = base64.b64encode(b"proxy-user:proxy-password")
            if authenticated and b"Proxy-Authorization: Basic " + token not in header:
                sock.sendall(b"HTTP/1.1 407 Proxy Authentication Required\r\n\r\n")
                return
            authority = header.split(b" ")[1].decode()
            host, port = authority.rsplit(":", 1)
            host = host.strip("[]")
            port = int(port)
        elif protocol == "socks5":
            version, count = exact(sock, 2)
            methods = exact(sock, count)
            selected = 2 if authenticated else 0
            if version != 5 or selected not in methods:
                sock.sendall(b"\5\xff")
                return
            sock.sendall(bytes([5, selected]))
            if authenticated:
                version, length = exact(sock, 2)
                user = exact(sock, length)
                password = exact(sock, exact(sock, 1)[0])
                if version != 1 or user != b"proxy-user" or password != b"proxy-password":
                    sock.sendall(b"\1\1")
                    return
                sock.sendall(b"\1\0")
            version, command, reserved, kind = exact(sock, 4)
            if version != 5 or command != 1 or reserved:
                return
            if kind == 1:
                host = socket.inet_ntop(socket.AF_INET, exact(sock, 4))
            elif kind == 4:
                host = socket.inet_ntop(socket.AF_INET6, exact(sock, 16))
            elif kind == 3:
                host = exact(sock, exact(sock, 1)[0]).decode()
            else:
                return
            port = struct.unpack("!H", exact(sock, 2))[0]
        else:
            version, command, port = struct.unpack("!BBH", exact(sock, 4))
            address = exact(sock, 4)
            user = terminated(sock)
            if version != 4 or command != 1 or (authenticated and user != b"proxy-user"):
                sock.sendall(b"\0\x5b" + b"\0" * 6)
                return
            host = terminated(sock).decode() if address[:3] == b"\0\0\0" and address[3] else socket.inet_ntoa(address)
        if host == "proxy-target.invalid":
            host = "127.0.0.1"
        if host not in ("127.0.0.1", "localhost", "::1"):
            return
        upstream = socket.create_connection((host, port), timeout=5)
        upstream.settimeout(None)
        response = (b"HTTP/1.1 200 Connection Established\r\nProxy-Agent: qssh-test\r\n\r\n" if protocol == "http" else
                    b"\5\0\0\1" + b"\0" * 6 if protocol == "socks5" else b"\0\x5a" + b"\0" * 6)
        # 分段写入协商响应，验证客户端不能依赖一次 read() 收到完整响应。
        sock.sendall(response[:1])
        sock.sendall(response[1:])
        sock.settimeout(None)
        reader = threading.Thread(target=copy, args=(upstream, sock), daemon=True)
        reader.start()
        copy(sock, upstream)
        reader.join(5)
    except (EOFError, OSError, ValueError, IndexError):
        pass
    finally:
        sock.close()
        if upstream:
            upstream.close()


def main(protocol, authenticated):
    """监听临时回环端口，为 protocol 提供可选 authenticated 凭据检查。"""
    listener = socket.socket()
    listener.bind(("127.0.0.1", 0))
    listener.listen()
    print(listener.getsockname()[1], flush=True)
    while True:
        sock, _ = listener.accept()
        threading.Thread(target=serve, args=(sock, protocol, authenticated), daemon=True).start()


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] == "auth")
