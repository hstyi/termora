package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.OptionsPane.Companion.formMargin
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.db.DatabaseManager
import com.formdev.flatlaf.extras.components.FlatLabel
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import kotlinx.serialization.json.*
import okhttp3.Request
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.binary.Hex
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.jdesktop.swingx.JXBusyLabel
import org.jdesktop.swingx.JXHyperlink
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.desktop.OpenURIEvent
import java.awt.desktop.OpenURIHandler
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.util.*
import javax.swing.*


class AccountOption : JPanel(BorderLayout()), OptionsPane.Option, Disposable {
    companion object {
        private val log = LoggerFactory.getLogger(AccountOption::class.java)
    }

    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val databaseManager get() = DatabaseManager.getInstance()
    private val accountManager get() = AccountManager.getInstance()
    private val accountProperties get() = AccountProperties.getInstance()
    private val cardLayout = CardLayout()
    private val rootPanel = JPanel(cardLayout)
    private val loginPanel = LoginPanel()
    private val userInfoPanel = JPanel(BorderLayout())

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        refreshUserInfoPanel()
        rootPanel.add(userInfoPanel, "UserInfo")
        rootPanel.add(loginPanel, "Login")
        cardLayout.show(rootPanel, "UserInfo")
        add(rootPanel, BorderLayout.CENTER)

        Disposer.register(this, loginPanel)
    }


    private fun initEvents() {}

    private fun getCenterComponent(): JComponent {
        val layout = FormLayout(
            "left:pref, $formMargin, default:grow",
            "pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref, $formMargin, pref"
        )

        var rows = 1
        val step = 2

        val subscription = accountManager.getSubscription()
        val isFreePlan = accountManager.isFreePlan()
        val isLocally = accountManager.isLocally()
        val validTo = if (isFreePlan) "-" else if (subscription.endAt >= Long.MAX_VALUE)
            I18n.getString("termora.settings.account.lifetime") else
            DateFormatUtils.format(Date(subscription.endAt), I18n.getString("termora.date-format"))
        val lastSynchronizationOn = if (isFreePlan) "-" else
            DateFormatUtils.format(
                Date(accountManager.getLastSynchronizationOn()),
                I18n.getString("termora.date-format")
            )

        var server = accountManager.getServer()
        var email = accountManager.getEmail()
        if (isLocally) {
            server = I18n.getString("termora.settings.account.locally")
            email = I18n.getString("termora.settings.account.locally")
        }

        val planBox = Box.createHorizontalBox()
        planBox.add(JLabel(if (isLocally) "-" else subscription.plan.name))
        if (isFreePlan && isLocally.not()) {
            planBox.add(Box.createHorizontalStrut(16))
            val upgrade = JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.upgrade")) {
                override fun actionPerformed(evt: AnActionEvent) {

                }
            })
            upgrade.isFocusable = false
            planBox.add(upgrade)
        }

        val serverBox = Box.createHorizontalBox()
        serverBox.add(JLabel(server))
        if (isLocally.not()) {
            serverBox.add(Box.createHorizontalStrut(8))
            if (accountManager.isSigned().not()) {
                val upgrade =
                    JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.verify"), Icons.error) {
                        override fun actionPerformed(evt: AnActionEvent) {
                            Application.browse(URI.create("https://www.termora.app"))
                        }
                    })
                upgrade.isFocusable = false
                serverBox.add(upgrade)
            } else {
                serverBox.add(JLabel(Icons.success))
            }
        }

        return FormBuilder.create().layout(layout).debug(false)
            .add("${I18n.getString("termora.settings.account.server")}:").xy(1, rows)
            .add(serverBox).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.account")}:").xy(1, rows)
            .add(email).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.account.subscription")}:").xy(1, rows)
            .add(planBox).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.account.valid-to")}:").xy(1, rows)
            .add(validTo).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.account.synchronization-on")}:").xy(1, rows)
            .add(lastSynchronizationOn).xy(3, rows).apply { rows += step }
            .add(createActionPanel(isFreePlan)).xyw(1, rows, 3).apply { rows += step }
            .build()
    }

    private fun createActionPanel(isFreePlan: Boolean): JComponent {
        val actionBox = Box.createHorizontalBox()
        actionBox.add(Box.createHorizontalGlue())
        val actions = mutableSetOf<JComponent>()

        if (accountManager.isLocally()) {
            actions.add(JXHyperlink(object : AnAction("${I18n.getString("termora.settings.account.login")}...") {
                override fun actionPerformed(evt: AnActionEvent) {
                    onLogin()
                }
            }).apply { isFocusable = false })
        } else {
            if (isFreePlan.not()) {
                actions.add(JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.sync-now")) {
                    override fun actionPerformed(evt: AnActionEvent) {
                        PullService.getInstance().trigger()
                        PushService.getInstance().trigger()
                        accountProperties.lastSynchronizationOn = System.currentTimeMillis()
                        refreshUserInfoPanel()
                    }
                }).apply { isFocusable = false })
            }

            actions.add(JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.logout")) {
                override fun actionPerformed(evt: AnActionEvent) {
                    val hasUnsyncedData = databaseManager.unsyncedData().isNotEmpty()
                    val message = if (hasUnsyncedData) "termora.settings.account.unsynced-logout-confirm"
                    else "termora.settings.account.logout-confirm"
                    val option = OptionPane.showConfirmDialog(
                        owner, I18n.getString(message),
                        optionType = JOptionPane.OK_CANCEL_OPTION,
                        messageType = JOptionPane.QUESTION_MESSAGE,
                    )
                    if (option != JOptionPane.OK_OPTION) {
                        return
                    }
                    AccountManager.getInstance().logout()
                    refreshUserInfoPanel()
                }
            }).apply { isFocusable = false })
        }

        for (component in actions) {
            actionBox.add(component)
            if (actions.last() != component) {
                actionBox.add(Box.createHorizontalStrut(8))
            }
        }

        actionBox.add(Box.createHorizontalGlue())



        return actionBox
    }

    private fun onLogin() {
        val dialog = ChooseLoginServerDialog(owner)
        dialog.isVisible = true

        val server = dialog.server ?: return
        val uri = URI.create(server.server)
        val url = StringBuilder()
        url.append(uri.scheme).append("://")
        url.append(uri.host)
        if (uri.port > 0) {
            url.append(":${uri.port}")
        }
        url.append(uri.path)

        loginPanel.login(url.toString())
        cardLayout.show(rootPanel, "Login")

    }

    private fun onLoginSuccess() {
        refreshUserInfoPanel()
        cardLayout.show(rootPanel, "UserInfo")
    }

    private fun refreshUserInfoPanel() {
        userInfoPanel.removeAll()
        userInfoPanel.add(getCenterComponent(), BorderLayout.CENTER)
        userInfoPanel.revalidate()
        userInfoPanel.repaint()
    }

    override fun getIcon(isSelected: Boolean): Icon {
        return Icons.user
    }

    override fun getTitle(): String {
        return I18n.getString("termora.settings.account")
    }

    override fun getJComponent(): JComponent {
        return this
    }

    private inner class LoginPanel : JPanel(BorderLayout()), Disposable, OpenURIHandler {
        private val busyLabel = JXBusyLabel(Dimension(32, 32))
        private val isLogin get() = busyLabel.isBusy
        private val loginLabel = FlatLabel()
        private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var ticket = StringUtils.EMPTY
        private var keypair: KeyPair? = null
        private var server: String? = null
        private var httpServer: HttpServer? = null

        init {

            // 注册回调
            OpenURIHandlers.getInstance().register(this)

            val layout = FormLayout(
                "default:grow, pref, default:grow",
                "20dlu, pref, 10dlu, pref, 10dlu, pref"
            )

            var rows = 2
            val step = 2

            val box = Box.createHorizontalBox()
            box.add(Box.createHorizontalGlue())
            box.add(busyLabel)
            box.add(Box.createHorizontalGlue())


            val cancelHyperlink = JXHyperlink(object : AnAction(I18n.getString("termora.cancel")) {
                override fun actionPerformed(evt: AnActionEvent) {
                    cancelLogin()
                }
            })
            cancelHyperlink.isFocusable = false

            val cancelHyperlinkBox = Box.createHorizontalBox()
            cancelHyperlinkBox.add(Box.createHorizontalGlue())
            cancelHyperlinkBox.add(cancelHyperlink)
            cancelHyperlinkBox.add(Box.createHorizontalGlue())


            val panel = FormBuilder.create().layout(layout).debug(false)
                .add(box).xy(2, rows).apply { rows += step }
                .add(loginLabel).xy(2, rows).apply { rows += step }
                .add(cancelHyperlinkBox).xy(2, rows).apply { rows += step }
                .build()

            add(panel, BorderLayout.CENTER)
        }

        fun login(server: String) {
            if (isLogin) return
            busyLabel.isBusy = true
            loginLabel.text = "${I18n.getString("termora.settings.account.login-to")} $server ..."

            val ticket = randomUUID()
            val keypair = RSA.generateKeyPair(2048)
            // 用户填写的密码回调时会通过它加密
            val publicKey = Base64.encodeBase64URLSafeString(keypair.public.encoded)
            var callback = StringUtils.EMPTY

            // 测试环境 通过 HTTP 回调
            if (Application.isUnknownVersion()) {
                this.httpServer?.stop(0)
                this.httpServer = null

                val httpServer = HttpServer.create(InetSocketAddress(0), 0)
                httpServer.createContext("/login-success") { exchange ->
                    val text = "OK".toByteArray()
                    exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
                    exchange.sendResponseHeaders(200, text.size.toLong())
                    exchange.responseBody.write(text, 0, text.size)
                    exchange.close()
                    onCallback(server, exchange.requestURI, keypair)
                }
                httpServer.start()

                val port = httpServer.address.port
                callback = URLEncoder.encode("http://127.0.0.1:${port}/login-success", Charsets.UTF_8)

                this.httpServer = httpServer
            }

            val loginUrl = "${server}/v1/client/login?ticket=${ticket}&publicKey=${publicKey}&callback=${callback}"

            // 打开登录页面
            Application.browse(URI.create(loginUrl))

            this.keypair = keypair
            this.ticket = ticket
            this.server = server


        }

        fun cancelLogin() {
            busyLabel.isBusy = false
            keypair = null
            server = null
            ticket = StringUtils.EMPTY
            httpServer?.stop(0)
            httpServer = null

            cardLayout.show(rootPanel, "UserInfo")
        }

        private fun loginSuccess(
            server: String,
            ticket: String,
            password: String,
            refreshToken: String,
            accessToken: String
        ) {
            val request = Request.Builder().url("${server}/v1/users/me")
                .header("Authorization", "Bearer $accessToken")
                .get().build()
            val text = AccountHttp.execute(request = request)
            val json = ohMyJson.decodeFromString<JsonObject>(text)

            val id = json["id"]?.jsonPrimitive?.content
            val email = json["email"]?.jsonPrimitive?.content
            val publicKeyBase64 = json["publicKey"]?.jsonPrimitive?.content
            val privateKeyBase64 = json["privateKey"]?.jsonPrimitive?.content
            val secretKeyBase64 = json["secretKey"]?.jsonPrimitive?.content
            val encryptedTeams = json["teams"]?.jsonArray
            val subscriptions = json["subscriptions"]?.jsonArray

            if (id == null || email == null || publicKeyBase64 == null || privateKeyBase64 == null || secretKeyBase64 == null) {
                throw IllegalStateException()
            }

            val salt = PBKDF2.hash("termora".toByteArray(), email.toCharArray(), 450000, 128)
            val privateKeyEncoded = Base64.decodeBase64(privateKeyBase64)
            val secretKeyEncrypted = Base64.decodeBase64(secretKeyBase64)
            val publicKeyEncoded = Base64.decodeBase64(publicKeyBase64)

            // 解密RSA私钥
            // @formatter:off
            val privateKeySecureKey = PBKDF2.hash(salt, "key:$email:$password".toCharArray(), 450000, 128)
            val privateKeySecureKeyIv = PBKDF2.hash(salt, "iv:$email:$password".toCharArray(), 450000, 96)
            val privateKey = RSA.generatePrivate(AES.GCM.decrypt(privateKeySecureKey, privateKeySecureKeyIv, privateKeyEncoded))
            // @formatter:on

            // 解密用户私钥
            val secretKey = RSA.decrypt(privateKey, secretKeyEncrypted)
            val teams = mutableListOf<Team>()

            if (encryptedTeams != null) {
                for (i in 0 until encryptedTeams.size) {
                    val team = encryptedTeams[i].jsonObject
                    val id = team["id"]?.jsonPrimitive?.content
                    val name = team["name"]?.jsonPrimitive?.content
                    val role = team["role"]?.jsonPrimitive?.content
                    val secretKeyBase64 = team["secretKey"]?.jsonPrimitive?.content
                    if (id == null || name == null || role == null || secretKeyBase64 == null) {
                        continue
                    }
                    teams.add(
                        Team(
                            id = id,
                            name = name,
                            secretKey = RSA.decrypt(privateKey, Base64.decodeBase64(secretKeyBase64)),
                            role = TeamRole.valueOf(role)
                        )
                    )
                }
            }

            // 登录成功
            val account = Account(
                id = id,
                server = server,
                email = email,
                teams = teams,
                subscriptions = if (subscriptions == null) emptyList()
                else ohMyJson.decodeFromJsonElement<List<Subscription>>(subscriptions),
                accessToken = accessToken,
                refreshToken = refreshToken,
                secretKey = secretKey,
                publicKey = RSA.generatePublic(publicKeyEncoded),
                privateKey = privateKey
            )

            // 登录成功
            SwingUtilities.invokeLater { accountManager.login(account) }

            httpServer?.stop(0)
            httpServer = null
        }

        override fun dispose() {
            busyLabel.isBusy = false
            httpServer?.stop(0)
            httpServer = null
            OpenURIHandlers.getInstance().unregister(this)
            coroutineScope.cancel()
        }

        override fun openURI(e: OpenURIEvent) {
            val uri = e.uri
            if (uri.host != "login-success") return
            if (uri.query == null) return
            val keypair = keypair ?: return
            val server = server ?: return
            onCallback(server, uri, keypair)
        }

        private fun onCallback(server: String, uri: URI, keypair: KeyPair) {

            try {
                val params = parseQuery(uri.query)
                val password = params["password"]
                val ticket = params["ticket"]
                val refreshToken = params["refreshToken"]
                val accessToken = params["accessToken"]
                if (password.isNullOrBlank() || ticket.isNullOrBlank() || refreshToken.isNullOrBlank() || accessToken.isNullOrBlank()) return
                if (ticket != this.ticket) return
                val realPassword = String(RSA.decrypt(keypair.private, Hex.decodeHex(password)))

                // 登录成功回调
                coroutineScope.launch {
                    try {

                        loginSuccess(server, ticket, realPassword, refreshToken, accessToken)

                        // 没有错误那么就回跳
                        withContext(Dispatchers.Swing) {
                            busyLabel.isBusy = false
                            onLoginSuccess()
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Swing) {
                            onLoginFailed(e)
                        }
                    }

                }
            } catch (e: Exception) {
                onLoginFailed(e)
            }
        }

        private fun onLoginFailed(e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.settings.account.login-failed"),
                messageType = JOptionPane.ERROR_MESSAGE
            )
            cancelLogin()
        }

        private fun parseQuery(query: String): Map<String, String> {
            val map = HashMap<String, String>()
            val pairs = query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (pair in pairs) {
                val kv: Array<String> = pair.split("=".toRegex(), limit = 2).toTypedArray()
                val key: String = URLDecoder.decode(kv[0], StandardCharsets.UTF_8)
                val value: String = if (kv.size > 1) URLDecoder.decode(kv[1], StandardCharsets.UTF_8) else ""
                map.put(key, value)
            }
            return map
        }
    }


}
