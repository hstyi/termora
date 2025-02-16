package app.termora

import com.pty4j.util.PtyUtil
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import java.io.File

fun main() {
    // 由于 macOS 签名和公证问题，依赖二进制依赖会单独在一个文件夹
    if (SystemUtils.IS_OS_MAC_OSX) {
        setupNativeLibraries()
    }

    if (SystemUtils.IS_OS_MAC_OSX) {
        System.setProperty("apple.awt.application.name", Application.getName())
    }

    ApplicationRunner().run()
}


private fun setupNativeLibraries() {
    if (!SystemUtils.IS_OS_MAC_OSX) {
        return
    }

    val appPath = Application.getAppPath()
    if (StringUtils.isBlank(appPath)) {
        return
    }

    val contents = File(appPath).parentFile?.parentFile ?: return
    val dylib = FileUtils.getFile(contents, "app", "dylib")
    if (!dylib.exists()) {
        return
    }

    val jna = FileUtils.getFile(dylib, "jna")
    if (jna.exists()) {
        System.setProperty("jna.boot.library.path", jna.absolutePath)
    }

    val pty4j = FileUtils.getFile(dylib, "pty4j")
    if (pty4j.exists()) {
        System.setProperty(PtyUtil.PREFERRED_NATIVE_FOLDER_KEY, pty4j.absolutePath)
    }

    val jSerialComm = FileUtils.getFile(dylib, "jSerialComm")
    if (jSerialComm.exists()) {
        System.setProperty("jSerialComm.library.path", jSerialComm.absolutePath)
    }
}