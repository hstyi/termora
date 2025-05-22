package app.termora.account

import app.termora.*
import app.termora.OptionsPane.Companion.formMargin
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import com.formdev.flatlaf.extras.components.FlatLabel
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.time.DateFormatUtils
import org.jdesktop.swingx.JXBusyLabel
import org.jdesktop.swingx.JXHyperlink
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.net.URI
import java.security.PrivateKey
import java.util.*
import javax.swing.*
import kotlin.time.Duration.Companion.seconds

class AccountOption : JPanel(BorderLayout()), OptionsPane.Option, Disposable {
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
            .add("Server:").xy(1, rows)
            .add(accountManager.getServer()).xy(3, rows).apply { rows += step }
            .add("Account:").xy(1, rows)
            .add(accountManager.getEmail()).xy(3, rows).apply { rows += step }
            .add("Subscription:").xy(1, rows)
            .add(subscription.plan.name).xy(3, rows).apply { rows += step }
            .add("Valid to:").xy(1, rows)
            .add(validTo).xy(3, rows).apply { rows += step }
            .add("Synchronization on:").xy(1, rows)
            .add(lastSynchronizationOn).xy(3, rows).apply { rows += step }
            .add(createActionPanel(isFreePlan)).xyw(1, rows, 3).apply { rows += step }
            .build()
    }

    private fun createActionPanel(isFreePlan: Boolean): JComponent {
        val actionBox = Box.createHorizontalBox()
        actionBox.add(Box.createHorizontalGlue())
        val actions = mutableSetOf<JComponent>()

        if (accountManager.isLocally()) {
            actions.add(JXHyperlink(object : AnAction("Login...") {
                override fun actionPerformed(evt: AnActionEvent) {
                    onLogin()
                }
            }).apply { isFocusable = false })
        } else {
            if (isFreePlan) {
                actions.add(JXHyperlink(object : AnAction("Upgrade") {
                    override fun actionPerformed(evt: AnActionEvent) {

                    }
                }).apply { isFocusable = false })
            } else {
                actions.add(JXHyperlink(object : AnAction("Sync now") {
                    override fun actionPerformed(evt: AnActionEvent) {

                    }
                }).apply { isFocusable = false })
            }

            actions.add(JXHyperlink(object : AnAction("Logout...") {
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

    private class LoginPanel : JPanel(BorderLayout()), Disposable {
        private val busyLabel = JXBusyLabel(Dimension(46, 46))
        private val isLogin get() = busyLabel.isBusy
        private val loginLabel = FlatLabel()

        init {

            val layout = FormLayout(
                "default:grow, pref, default:grow",
                "20dlu, pref, 10dlu, pref"
            )

            var rows = 2
            val step = 2

            val box = Box.createHorizontalBox()
            box.add(Box.createHorizontalGlue())
            box.add(busyLabel)
            box.add(Box.createHorizontalGlue())

            val panel = FormBuilder.create().layout(layout).debug(true)
                .add(box).xy(2, rows).apply { rows += step }
                .add(loginLabel).xy(2, rows).apply { rows += step }
                .build()

            add(panel, BorderLayout.CENTER)
        }

        fun login(server: String) {
            if (isLogin) return
            busyLabel.isBusy = true
            loginLabel.text = "登录到 $server ..."


            val uuid = randomUUID() + randomUUID()
            val keypair = RSA.generateKeyPair(2048)
            val publicKey = Base64.encodeBase64URLSafeString(keypair.public.encoded)
            val loginUrl = "${server}/v1/client/login?ticket=${uuid}&publicKey=${publicKey}"

            // 打开登录页面
            Application.browse(URI.create(loginUrl))

            // 检查登录情况
            swingCoroutineScope.launch(Dispatchers.IO) { checkLogin(server, uuid, keypair.private) }
        }

        private fun checkLogin(server: String, ticket: String, privateKey: PrivateKey) {
            if (busyLabel.isBusy.not()) return

            println("Check...")


            swingCoroutineScope.launch(Dispatchers.IO) {
                delay(1.5.seconds)
                checkLogin(server, ticket, privateKey)
            }

        }


        fun cancel() {
            busyLabel.isBusy = false
        }

        override fun dispose() {
            busyLabel.isBusy = false
        }
    }

}
