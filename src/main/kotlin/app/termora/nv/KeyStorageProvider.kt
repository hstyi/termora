package app.termora.nv

import app.termora.ApplicationScope
import app.termora.nv.osx.MacOSKeyStorage
import app.termora.nv.win32.WindowsKeyStorage
import com.formdev.flatlaf.util.SystemInfo

class KeyStorageProvider private constructor() {

    companion object {
        fun getInstance(): KeyStorageProvider {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeyStorageProvider::class) { KeyStorageProvider() }
        }
    }

    fun getKeyStorage(): KeyStorage {
        return if (SystemInfo.isMacOS) MacOSKeyStorage.getInstance()
        else if (SystemInfo.isWindows) WindowsKeyStorage.getInstance()
        else MacOSKeyStorage.getInstance()
    }
}