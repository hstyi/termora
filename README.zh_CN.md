# Termora

**Termora** 是一个终端模拟器和 SSH 客户端，支持 Windows，macOS 和 Linux。

<div align="center">
  <img src="./docs/readme-zh_CN.png" alt="termora" />
</div>

**Termora** 采用 [Kotlin/JVM](https://kotlinlang.org/) 开发并实现了 [XTerm](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html) 协议（尚未完全实现），它的最终目标是通过 [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) 实现全平台（含 Android、iOS、iPadOS 等）。

## 功能特性

- 支持 SSH 和本地终端
- 支持串口协议
- 支持 [SFTP](./docs/sftp-zh_CN.png?raw=1) & [命令行](./docs/sftp-command.png?raw=1) 文件传输
- 支持 Windows、macOS、Linux 平台
- 支持 Zmodem 协议
- 支持 SSH 端口转发和跳板机
- 支持 X11 和 SSH-Agent
- 终端日志记录
- 支持配置同步到 [Gist](https://gist.github.com) & [WebDAV](https://developer.mozilla.org/docs/Glossary/WebDAV)
- 支持宏（录制脚本并回放）
- 支持关键词高亮
- 支持密钥管理器
- 支持将命令发送到多个会话
- 支持 [Find Everywhere](./docs/findeverywhere-zh_CN.png?raw=1) 快速跳转
- 支持数据加密
- ...

## 下载

- [Latest release](https://github.com/TermoraDev/termora/releases/latest)
- [Homebrew](https://formulae.brew.sh/cask/termora): `brew install --cask termora`
- [WinGet](https://github.com/microsoft/winget-pkgs/tree/master/manifests/t/TermoraDev/Termora): `winget install termora`

## 开发

建议使用 [JetBrainsRuntime](https://github.com/JetBrains/JetBrainsRuntime) 的 JDK 版本，通过 `./gradlew :run` 即可运行程序。

通过 `./gradlew dist` 可以自动构建适用于本机的版本。在 macOS 上是：`dmg`，在 Windows 上是：`zip`，在 Linux 上是：`tar.gz`。

## 协议

本软件采用双重许可模式，您可以选择以下任意一种许可方式：

- AGPL-3.0：根据 [AGPL-3.0](https://opensource.org/license/agpl-v3) 的条款，您可以自由使用、分发和修改本软件。
- 专有许可：如果希望在闭源或专有环境中使用，请联系作者获取许可。
