package app.termora.plugin.internal.wsl

import com.formdev.flatlaf.util.SystemInfo
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils


object WSLSupport {
    val isSupported by lazy { checkSupported() }

    private fun checkSupported(): Boolean {
        if (SystemInfo.isWindows.not()) return false
        val drive = System.getenv("SystemRoot") ?: return false
        val wsl = FileUtils.getFile(drive, "System32", "wsl.exe")
        return wsl.exists()
    }

    fun getDistributions(): List<WSLDistribution> {
        if (isSupported.not()) return emptyList()

        val baseKeyPath = "Software\\Microsoft\\Windows\\CurrentVersion\\Lxss"
        val guids = Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, baseKeyPath)
        val distributions = mutableListOf<WSLDistribution>()

        for (guid in guids) {
            val key = baseKeyPath + "\\" + guid
            val distroName = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, "DistributionName")
            val basePath = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, "BasePath")
            val flavor = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, "Flavor")
            if (StringUtils.isAnyBlank(distroName, guid, basePath, flavor)) continue
            distributions.add(
                WSLDistribution(
                    guid = guid,
                    flavor = flavor,
                    basePath = basePath,
                    distributionName = distroName
                )
            )
        }

        return distributions
    }
}