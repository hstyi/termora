package app.termora.native

import app.termora.ApplicationScope
import app.termora.native.osx.MacOSKeyStorage
import com.formdev.flatlaf.util.SystemInfo

class KeyStorageProvider private constructor() {

    companion object {
        fun getInstance(): KeyStorageProvider {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeyStorageProvider::class) { KeyStorageProvider() }
        }
    }

    fun getKeyStorage(): KeyStorage {
        return if (SystemInfo.isMacOS) MacOSKeyStorage.getInstance() else MacOSKeyStorage.getInstance()
    }
}