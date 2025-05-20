package app.termora.account

import app.termora.ApplicationScope

class AccountManager private constructor() {
    companion object {
        fun getInstance(): AccountManager {
            return ApplicationScope.forApplicationScope().getOrCreate(AccountManager::class) { AccountManager() }
        }
    }

    fun getAccountId(): String {
        return "0"
    }
}