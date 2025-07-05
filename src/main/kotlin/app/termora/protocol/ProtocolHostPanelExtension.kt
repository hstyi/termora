package app.termora.protocol

import app.termora.account.AccountOwner
import app.termora.plugin.Extension
import app.termora.plugin.ExtensionManager


interface ProtocolHostPanelExtension : Extension {
    companion object {
        val extensions
            get() = ExtensionManager.getInstance()
                .getExtensions(ProtocolHostPanelExtension::class.java)
    }

    /**
     * 获取协议提供者
     */
    fun getProtocolProvider(): ProtocolProvider

    /**
     * 是否可以创建协议主机面板
     */
    @Deprecated("Old stuff")
    fun canCreateProtocolHostPanel(): Boolean = true

    /**
     * 是否可以创建协议主机面板
     */
    fun canCreateProtocolHostPanel(accountOwner: AccountOwner) = canCreateProtocolHostPanel()

    /**
     * 创建协议主机面板
     */
    @Deprecated("Old stuff")
    fun createProtocolHostPanel(): ProtocolHostPanel = throw UnsupportedOperationException()

    /**
     * 创建协议主机面板
     */
    fun createProtocolHostPanel(accountOwner: AccountOwner) = createProtocolHostPanel()

}