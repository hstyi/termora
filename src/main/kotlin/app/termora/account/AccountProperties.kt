package app.termora.account

import app.termora.ApplicationScope
import app.termora.db.DatabaseManager
import org.apache.commons.lang3.StringUtils

/**
 * 账号配置
 */
class AccountProperties private constructor(databaseManager: DatabaseManager, val id: String) :
    DatabaseManager.IProperties(databaseManager, "Setting.Account.$id") {

    companion object {
        fun getInstance(): AccountProperties {
            val databaseManager = DatabaseManager.getInstance()
            val accountId = databaseManager.properties.getString("CurrentAccountId") ?: "0"
            return ApplicationScope.forApplicationScope()
                .getOrCreate(AccountProperties::class) { AccountProperties(databaseManager, accountId) }
        }
    }


    /**
     * server
     */
    var server by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * email
     */
    var email by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * team
     */
    var teams by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * team
     */
    var subscriptions by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * 最后同步时间
     */
    var lastSynchronizationOn by LongPropertyDelegate(0)

    /**
     * 用户密钥，array string
     */
    var secretKey by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * 用户公钥匙，base64
     */
    var publicKey by StringPropertyDelegate(StringUtils.EMPTY)

    /**
     * 用户私钥，base64
     */
    var privateKey by StringPropertyDelegate(StringUtils.EMPTY)

}