package app.termora.account

import app.termora.*
import app.termora.OptionsPane.Companion.formMargin
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import com.formdev.flatlaf.extras.components.FlatLabel
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import okhttp3.Request
import org.apache.commons.codec.binary.Base64
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
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.util.*
import javax.swing.*
import kotlin.time.Duration.Companion.seconds


class AccountOption : JPanel(BorderLayout()), OptionsPane.Option, Disposable {
    companion object {
        private val log = LoggerFactory.getLogger(AccountOption::class.java)
    }

    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val accountManager get() = AccountManager.getInstance()
    private val cardLayout = CardLayout()
    private val rootPanel = JPanel(cardLayout)
    private val loginPanel = LoginPanel()

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        rootPanel.add(getCenterComponent(), "UserInfo")
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
        val isFreePlan = accountManager.isLocally() || subscription.endDate == Long.MAX_VALUE
                || subscription.plan == SubscriptionPlan.Free

        val validTo = if (isFreePlan) "-" else
            DateFormatUtils.format(Date(subscription.endDate), I18n.getString("termora.date-format"))
        val lastSynchronizationOn = if (isFreePlan) "-" else
            DateFormatUtils.format(
                Date(accountManager.getLastSynchronizationOn()),
                I18n.getString("termora.date-format")
            )


        return FormBuilder.create().layout(layout).debug(false)
            .add("${I18n.getString("termora.settings.account.server")}:").xy(1, rows)
            .add(accountManager.getServer()).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.account")}:").xy(1, rows)
            .add(accountManager.getEmail()).xy(3, rows).apply { rows += step }
            .add("${I18n.getString("termora.settings.account.subscription")}:").xy(1, rows)
            .add(subscription.plan.name).xy(3, rows).apply { rows += step }
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
            if (isFreePlan) {
                actions.add(JXHyperlink(object : AnAction(I18n.getString("termora.settings.plugin.subscribe")) {
                    override fun actionPerformed(evt: AnActionEvent) {

                    }
                }).apply { isFocusable = false })
            } else {
                actions.add(JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.sync-now")) {
                    override fun actionPerformed(evt: AnActionEvent) {

                    }
                }).apply { isFocusable = false })
            }

            actions.add(JXHyperlink(object : AnAction("${I18n.getString("termora.settings.account.logout")}...") {
                override fun actionPerformed(evt: AnActionEvent) {

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
                    cancel()
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
            val loginUrl = "${server}/v1/client/login?ticket=${ticket}&publicKey=${publicKey}"

            // 打开登录页面
            Application.browse(URI.create(loginUrl))

            this.keypair = keypair
            this.ticket = ticket
            this.server = server

            // 测试环境
            if (Application.isUnknownVersion()) {
                coroutineScope.launch(Dispatchers.IO) {
                    delay(3.seconds)
                    withContext(Dispatchers.Swing) {
                        OpenURIHandlers.getInstance().trigger(
                            OpenURIEvent(
                                URI.create(
                                    "termora://login-success?ticket=0196f61bc34d7742978e6c30725733770196f61bc34d76a396c06751a9f4f5d1&refreshToken=eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ0ZXJtb3JhLWJhY2tlbmQiLCJleHAiOjE3NTA1NjcxNTUsInN1YiI6IlJlZnJlc2hUb2tlbiIsImVtYWlsIjoiODg4QHFxLmNvbSIsImlkIjoiZmQzMGU5ZTctOWJhMi00ZWJiLTg3MGItOGZhOWVlYmNkYzNkIiwiZGlnZXN0IjoiOGZjMDVhYTgifQ.oV0NpI_kkF2HJuW0ISvcZskBlsyyudaPc9j_tUvkoi3fu3XjKjyqEWExxHnnCXeYilmmYlMFBnAjvVd5hVXgPQ&accessToken=eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJ0ZXJtb3JhLWJhY2tlbmQiLCJleHAiOjE3NDc4OTA1NTUsInN1YiI6IkFjY2Vzc1Rva2VuIiwiZW1haWwiOiI4ODhAcXEuY29tIiwiaWQiOiJmZDMwZTllNy05YmEyLTRlYmItODcwYi04ZmE5ZWViY2RjM2QifQ.tQI8vDP1IhEMeoXL9mAU-VVEtyqUez7m9bqTmAYC7f2DNSC5dAmFiOfZLnLkaaPdQafaiFBs9QOgqRlN27i1PQ&password=HI3H5xOohZ6YsNq8MAu6hmT8l68jfOMxC5VRXdwJStIHvSRgb9ECvV5XzbkBFHImIaO61+GXNQvENzWWZ9d1KKG76T6CbE1klpBeeZtj5yvutgAa608VklOUHwzGS6aFBPo5S2lqmTMSaso3jpyTBsWx4IgEwctf5VhjQMqLcw9LvKP9Z4PiZgQw1yowihYOdrUEGFw3g02P1zS8fu5lfR+tZFm01ZrMnv7uHaJ6BiuoJjxxY5XHhqZre85gVcUKlBgNR5zf4UFOx9++kz8MNO58rlIv/geKKEHBrQhEppXCPBG6O8aUzJdmjYT0bG/vjUPUDlsT9ymlf1WxIltMgQ=="
                                )
                            )
                        )
                    }
                }
            }

        }

        fun cancel() {
            busyLabel.isBusy = false
            keypair = null
            server = null
            ticket = StringUtils.EMPTY

            cardLayout.show(rootPanel, "UserInfo")
        }

        private fun loginSuccess(
            server: String,
            ticket: String,
            password: String,
            refreshToken: String,
            accessToken: String
        ) {
            val response = AccountHttp.client.newCall(
                Request.Builder().url("${server}/v1/users/me")
                    .get().build()
            ).execute()

        }

        override fun dispose() {
            busyLabel.isBusy = false
            OpenURIHandlers.getInstance().unregister(this)
            coroutineScope.cancel()
        }

        override fun openURI(e: OpenURIEvent) {
            val uri = e.uri
            if (uri.host != "login-success") return
            if (uri.query == null) return

            try {
                onCallback(uri)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
                OptionPane.showMessageDialog(
                    owner,
                    I18n.getString("termora.settings.account.login-failed"),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
                cancel()
            }
        }

        private fun onCallback(uri: URI) {
            val keypair = keypair ?: return
            val server = server ?: return

            val params = parseQuery(uri.query)
            val password = params["password"]
            val ticket = params["ticket"]
            val refreshToken = params["refreshToken"]
            val accessToken = params["accessToken"]
            if (password.isNullOrBlank() || ticket.isNullOrBlank() || refreshToken.isNullOrBlank() || accessToken.isNullOrBlank()) return

            // 解密密码
            val realPassword = String(RSA.decrypt(keypair.private, Base64.decodeBase64(password)))

            // 登录成功回调
            coroutineScope.launch { loginSuccess(server, ticket, realPassword, refreshToken, accessToken) }
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
