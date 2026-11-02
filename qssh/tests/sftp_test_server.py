"""SFTP 专用测试支持；全部文件操作限制在用例的临时目录。"""

import errno
import os
import struct
import time

import paramiko


class TestFileHandle(paramiko.SFTPHandle):
    """按测试路径模拟短读，其他文件仍采用 Paramiko 标准实现。"""
    def read(self, offset, length):
        """短读不表示 EOF，客户端必须继续补齐同一区间。"""
        return super().read(offset, min(length, 8191) if self.test_path.endswith("short-read") else length)


class TestSftpInterface(paramiko.SFTPServerInterface):
    """将 SFTP v3 操作映射到 root，不允许访问测试目录之外的路径。"""

    def __init__(self, server, root):
        """保存隔离 root；server 是当前 SSH 连接的测试认证接口。"""
        super().__init__(server)
        self.root = os.path.realpath(root)  # 所有操作的隔离根。
        self.fault = None  # 下一个响应的故障注入类型。

    def local_path(self, path, follow=True):
        """将远端 path 映射到隔离目录；follow 控制是否跟随最后一级符号链接。"""
        candidate = os.path.normpath(os.path.join(self.root, path.lstrip("/")))
        resolved = os.path.realpath(candidate if follow else os.path.dirname(candidate))
        if os.path.commonpath((self.root, candidate)) != self.root or os.path.commonpath((self.root, resolved)) != self.root:
            raise PermissionError(errno.EACCES, "Outside test root")
        return candidate

    def list_folder(self, path):
        """返回 path 的目录属性，Paramiko 自动分批发送 NAME 响应。"""
        try:
            directory = self.local_path(path)
            entries = []
            for name in os.listdir(directory):
                info = paramiko.SFTPAttributes.from_stat(os.lstat(os.path.join(directory, name)))
                info.filename = name
                entries.append(info)
            return entries
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)

    def stat(self, path):
        """获取 path 属性；保留几个虚拟路径用于慢响应、超时和格式错误测试。"""
        if path == "/slow":
            time.sleep(0.6)
            return paramiko.SFTPAttributes.from_stat(os.stat(self.root))
        if path == "/stall":
            time.sleep(10)
            return paramiko.SFTP_NO_SUCH_FILE
        if path in ("/bad-id", "/oversized", "/truncated", "/wrong-type"):
            self.fault = path[1:]
            return paramiko.SFTP_NO_SUCH_FILE
        if path == "/denied":
            return paramiko.SFTP_PERMISSION_DENIED
        if path == "/fragmented":
            self.fault = "fragmented"
            return paramiko.SFTPAttributes.from_stat(os.stat(self.root))
        try:
            info = paramiko.SFTPAttributes.from_stat(os.stat(self.local_path(path)))
            if path == "/unknown-size":
                info.st_size = None
            return info
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)

    def lstat(self, path):
        """获取 path 本身属性，不跟随最后一级符号链接。"""
        try:
            return paramiko.SFTPAttributes.from_stat(os.lstat(self.local_path(path, False)))
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)

    def canonicalize(self, path):
        """将 path 规范化为隔离根下的远端绝对路径。"""
        return os.path.normpath("/" + path.lstrip("/"))

    def open(self, path, flags, attr):
        """以 flags 打开隔离 path，返回 Paramiko 文件句柄。"""
        if path == "/late-open":
            print("SFTP_OPEN_STARTED", flush=True)
            time.sleep(0.3)
        try:
            descriptor = os.open(self.local_path(path), flags, attr.st_mode if attr.st_mode is not None else 0o644)
            mode = "r+b" if flags & os.O_RDWR else "wb" if flags & os.O_WRONLY else "rb"
            stream = os.fdopen(descriptor, mode, buffering=0)
            handle = TestFileHandle(flags)
            handle.test_path = path
            if flags & os.O_RDWR or not flags & os.O_WRONLY:
                handle.readfile = stream
            if flags & (os.O_WRONLY | os.O_RDWR):
                handle.writefile = stream
            return handle
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)

    def mkdir(self, path, attr):
        """以 attr 权限创建 path 目录。"""
        return self.change(os.mkdir, self.local_path(path), attr.st_mode if attr.st_mode is not None else 0o755)

    def rmdir(self, path):
        """删除空目录 path。"""
        return self.change(os.rmdir, self.local_path(path))

    def remove(self, path):
        """删除 path 文件或符号链接。"""
        return self.change(os.unlink, self.local_path(path, False))

    def rename(self, oldpath, newpath):
        """遵守 SFTP v3 无覆盖语义，将 oldpath 重命名为 newpath。"""
        if os.path.lexists(self.local_path(newpath, False)):
            return paramiko.SFTP_FAILURE
        return self.change(os.rename, self.local_path(oldpath, False), self.local_path(newpath, False))

    def chattr(self, path, attr):
        """为 path 设置测试覆盖的 POSIX 权限。"""
        return self.change(os.chmod, self.local_path(path), attr.st_mode)

    def symlink(self, target_path, path):
        """创建隔离 path 链接；绝对远端目标也只映射到测试 root。"""
        target = self.local_path(target_path) if target_path.startswith("/") else target_path
        return self.change(os.symlink, target, self.local_path(path, False))

    def readlink(self, path):
        """返回 path 链接目标，将隔离本机路径还原成远端绝对路径。"""
        try:
            target = os.readlink(self.local_path(path, False))
            return "/" + os.path.relpath(target, self.root) if target.startswith(self.root + os.sep) else target
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)

    @staticmethod
    def change(operation, *args):
        """执行 operation(args)，将本机错误转换为 SFTP 状态。"""
        try:
            operation(*args)
            return paramiko.SFTP_OK
        except OSError as error:
            return paramiko.SFTPServer.convert_errno(error.errno)


class FragmentedSftpServer(paramiko.SFTPServer):
    """把每个响应拆成多个 SSH write，验证客户端独立增量解析及错误处理。"""

    def _process(self, packet_type, request_id, message):
        """指定文件必须收到两个内容请求后才回复，并逆序回复以验证真正流水线。"""
        if packet_type in (5, 6):
            peek = paramiko.Message(message.get_remainder())
            handle = self.file_table.get(peek.get_binary())
            if handle and getattr(handle, "test_path", "").startswith("/pipeline-"):
                pending = getattr(self, "pending_pipeline", [])
                pending.append((packet_type, request_id, paramiko.Message(message.get_remainder())))
                self.pending_pipeline = pending
                if len(pending) == 1:
                    print("SFTP_FIRST_DATA_REQUEST", flush=True)
                if len(pending) == 2:
                    self.pending_pipeline = []
                    for args in reversed(pending):
                        super()._process(*args)
                return
        super()._process(packet_type, request_id, message)

    def _send_packet(self, packet_type, packet):
        """分片发送 packet_type/packet，并按虚拟路径注入不合法的响应。"""
        body = bytes((packet_type,)) + packet.asbytes()
        fault, self.server.fault = self.server.fault, None
        if fault == "bad-id":
            body = body[:1] + struct.pack(">I", 0xFFFFFFFE) + body[5:]
        elif fault == "truncated":
            body = body[:6]
        elif fault == "wrong-type":
            body = bytes((103,)) + body[1:]  # STAT 不应收到 DATA。
        frame = struct.pack(">I", len(body)) + body
        if fault == "oversized":
            frame = struct.pack(">I", 2 * 1024 * 1024) + body
        # 分开长度前缀本身和正文；解析不能依赖 SSH read 与 SFTP 报文边界一致。
        self._write_all(frame[:1])
        if fault == "fragmented" or packet_type == 2:
            time.sleep(0.01)
        self._write_all(frame[1:3])
        if fault == "fragmented" or packet_type == 2:
            time.sleep(0.01)
        self._write_all(frame[3:9])
        if fault == "fragmented" or packet_type == 2:
            time.sleep(0.01)
        self._write_all(frame[9:])
