package app.termora.native.osx

import app.termora.ApplicationScope
import app.termora.native.KeyStorage
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.CoreFoundation.*
import com.sun.jna.ptr.PointerByReference


class MacOSKeyStorage private constructor() : KeyStorage {


    companion object {
        fun getInstance(): MacOSKeyStorage {
            return ApplicationScope.forApplicationScope().getOrCreate(MacOSKeyStorage::class) { MacOSKeyStorage() }
        }

        private val errSecItemNotFound = -25300
        private val errSecSuccess = 0

        private val Pointer.pointer get() = getPointer(0)
        private val coreFoundation by lazy { INSTANCE }
        private val security by lazy { Security.instance }
        private val nativeLibrary by lazy { Native.getNativeLibrary(coreFoundation) }
        private val kCFTypeDictionaryKeyCallBacks by lazy { nativeLibrary.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks").pointer }
        private val kCFTypeDictionaryValueCallBacks by lazy { nativeLibrary.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks").pointer }
        private val kCFBooleanTrue by lazy { nativeLibrary.getGlobalVariableAddress("kCFBooleanTrue").pointer }
    }

    /**
     * https://developer.apple.com/documentation/security/?language=objc
     */
    private interface Security : Library {

        companion object {
            val instance by lazy { Native.load("Security", Security::class.java) }

            private val nativeLibrary by lazy { Native.getNativeLibrary(instance) }
            val kSecAttrService by lazy { nativeLibrary.getGlobalVariableAddress("kSecAttrService").pointer }
            val kSecClass by lazy { nativeLibrary.getGlobalVariableAddress("kSecClass").pointer }
            val kSecAttrAccount by lazy { nativeLibrary.getGlobalVariableAddress("kSecAttrAccount").pointer }
            val kSecValueData by lazy { nativeLibrary.getGlobalVariableAddress("kSecValueData").pointer }
            val kSecClassGenericPassword by lazy { nativeLibrary.getGlobalVariableAddress("kSecClassGenericPassword").pointer }
            val kSecReturnAttributes by lazy { nativeLibrary.getGlobalVariableAddress("kSecReturnAttributes").pointer }
            val kSecReturnRef by lazy { nativeLibrary.getGlobalVariableAddress("kSecReturnRef").pointer }
            val kSecReturnData by lazy { nativeLibrary.getGlobalVariableAddress("kSecReturnData").pointer }
            val kSecMatchCaseInsensitive by lazy { nativeLibrary.getGlobalVariableAddress("kSecMatchCaseInsensitive").pointer }
            val kSecMatchLimit by lazy { nativeLibrary.getGlobalVariableAddress("kSecMatchLimit").pointer }
            val kSecMatchLimitOne by lazy { nativeLibrary.getGlobalVariableAddress("kSecMatchLimitOne").pointer }
            val kSecMatchLimitAll by lazy { nativeLibrary.getGlobalVariableAddress("kSecMatchLimitAll").pointer }
        }

        fun SecItemAdd(attributes: CFDictionaryRef, result: CFTypeRef?): Int
        fun SecItemDelete(attributes: CFDictionaryRef): Int
        fun SecItemCopyMatching(query: CFDictionaryRef, result: Pointer): Int
    }


    override fun setPassword(serviceName: String, username: String, password: String): Boolean {


        // 先删除
        deletePassword(serviceName, username)

        val query = coreFoundation.CFDictionaryCreateMutable(
            coreFoundation.CFAllocatorGetDefault(),
            CFIndex(0),
            kCFTypeDictionaryKeyCallBacks,
            kCFTypeDictionaryValueCallBacks
        )

        // 通用密码
        query.setValue(CFTypeRef(Security.kSecClass), CFTypeRef(Security.kSecClassGenericPassword))

        // service name
        val cfServiceName = CFStringRef.createCFString(serviceName)
        query.setValue(CFTypeRef(Security.kSecAttrService), cfServiceName)

        // username
        val cfUsername = CFStringRef.createCFString(username)
        query.setValue(CFTypeRef(Security.kSecAttrAccount), cfUsername)

        // password
        val bytes = password.toByteArray()
        val passwordMemory = Memory(bytes.size.toLong())
        passwordMemory.write(0, bytes, 0, bytes.size)
        val cfPassword = coreFoundation.CFDataCreate(
            coreFoundation.CFAllocatorGetDefault(), passwordMemory,
            CFIndex(passwordMemory.size())
        )
        query.setValue(CFTypeRef(Security.kSecValueData), cfPassword)


        val code = security.SecItemAdd(query, null)

        cfUsername.release()
        cfServiceName.release()
        cfPassword.release()
        query.release()

        return errSecSuccess == code

    }

    override fun getPassword(serviceName: String, username: String): String? {

        val query = coreFoundation.CFDictionaryCreateMutable(
            coreFoundation.CFAllocatorGetDefault(),
            CFIndex(0),
            null,
            null
        )

        // 通用密码
        query.setValue(CFTypeRef(Security.kSecClass), CFTypeRef(Security.kSecClassGenericPassword))
        // 服务名称
        val cfServiceName = CFStringRef.createCFString(serviceName)
        query.setValue(CFTypeRef(Security.kSecAttrService), cfServiceName)
        // 账号名称
        val cfUsername = CFStringRef.createCFString(username)
        query.setValue(CFTypeRef(Security.kSecAttrAccount), cfUsername)
        // 返回数据
        query.setValue(CFTypeRef(Security.kSecReturnData), CFBooleanRef(kCFBooleanTrue))
        query.setValue(CFTypeRef(Security.kSecReturnRef), CFBooleanRef(kCFBooleanTrue))
        query.setValue(CFTypeRef(Security.kSecReturnAttributes), CFBooleanRef(kCFBooleanTrue))
        query.setValue(CFTypeRef(Security.kSecMatchLimit), CFTypeRef(Security.kSecMatchLimitOne))
        // 大小写敏感
        query.setValue(CFTypeRef(Security.kSecMatchCaseInsensitive), CFBooleanRef(kCFBooleanTrue))

        var password: String? = null
        var cfDictionary: CFDictionaryRef? = null
        val result = PointerByReference()
        val code = security.SecItemCopyMatching(query, result.pointer)

        // search
        if (code == errSecSuccess) {
            do {
                cfDictionary = CFDictionaryRef(result.value)
                val cfAccount = cfDictionary.getValue(CFTypeRef(Security.kSecAttrAccount))
                if (cfAccount == null || cfAccount == Pointer.NULL) {
                    break
                }

                // 名称不匹配跳出
                if (CFStringRef(cfAccount).stringValue() != username) {
                    break
                }

                val cfPassword = cfDictionary.getValue(CFTypeRef(Security.kSecValueData))
                if (cfPassword == null || cfPassword == Pointer.NULL) {
                    break
                }

                password = CFDataRef(cfPassword).bytePtr.getString(0)
            } while (false)

        }

        cfDictionary?.release()
        cfServiceName.release()
        query.release()

        return password
    }

    fun getPasswords(serviceName: String): List<Pair<String, String>> {
        val query = coreFoundation.CFDictionaryCreateMutable(
            coreFoundation.CFAllocatorGetDefault(),
            CFIndex(0),
            null,
            null
        )

        // 通用密码
        query.setValue(CFTypeRef(Security.kSecClass), CFTypeRef(Security.kSecClassGenericPassword))
        // 服务名称
        val cfServiceName = CFStringRef.createCFString(serviceName)
        query.setValue(CFTypeRef(Security.kSecAttrService), cfServiceName)
        // 返回数据
//        query.setValue(CFTypeRef(Security.kSecReturnData), CFBooleanRef(kCFBooleanTrue))
        query.setValue(CFTypeRef(Security.kSecReturnRef), CFBooleanRef(kCFBooleanTrue))
        query.setValue(CFTypeRef(Security.kSecReturnAttributes), CFBooleanRef(kCFBooleanTrue))
        query.setValue(CFTypeRef(Security.kSecMatchLimit), CFTypeRef(Security.kSecMatchLimitAll))
        // 大小写敏感
        query.setValue(CFTypeRef(Security.kSecMatchCaseInsensitive), CFBooleanRef(kCFBooleanTrue))

        var cfArray: CFArrayRef? = null
        val result = PointerByReference()
        val code = security.SecItemCopyMatching(query, result.pointer)
        val list = mutableListOf<Pair<String, String>>()

        // search
        if (code == errSecSuccess) {
            cfArray = CFArrayRef(result.value)
            for (i in 0 until cfArray.count) {
                val cfDictionary = CFDictionaryRef(cfArray.getValueAtIndex(i))
                val cfAccount = cfDictionary.getValue(CFTypeRef(Security.kSecAttrAccount))
                if (cfAccount == null || cfAccount == Pointer.NULL) {
                    break
                }

                val username = CFStringRef(cfAccount).stringValue()
                val password = getPassword(serviceName, CFStringRef(cfAccount).stringValue())

                if (password != null) {
                    list.add(Pair(username, password))
                }
            }

        }

        cfArray?.release()
        cfServiceName.release()
        query.release()

        return list
    }

    override fun deletePassword(serviceName: String, username: String): Boolean {
        val query = coreFoundation.CFDictionaryCreateMutable(
            coreFoundation.CFAllocatorGetDefault(),
            CFIndex(0),
            kCFTypeDictionaryKeyCallBacks,
            kCFTypeDictionaryValueCallBacks
        )

        // 通用密码
        query.setValue(CFTypeRef(Security.kSecClass), CFTypeRef(Security.kSecClassGenericPassword))

        // service name
        val cfServiceName = CFStringRef.createCFString(serviceName)
        query.setValue(CFTypeRef(Security.kSecAttrService), cfServiceName)

        // username
        val cfUsername = CFStringRef.createCFString(username)
        query.setValue(CFTypeRef(Security.kSecAttrAccount), cfUsername)

        val code = security.SecItemDelete(query)

        cfUsername.release()
        cfServiceName.release()
        query.release()

        return code == errSecItemNotFound || code == errSecSuccess
    }


}