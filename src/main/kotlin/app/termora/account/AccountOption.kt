package app.termora.account

import app.termora.Application
import app.termora.I18n
import app.termora.Icons
import app.termora.OptionsPane
import app.termora.OptionsPane.Companion.formMargin
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import com.formdev.flatlaf.extras.components.FlatLabel
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.time.DateFormatUtils
import org.jdesktop.swingx.JXHyperlink
import java.awt.BorderLayout
import java.util.*
import javax.swing.*

class AccountOption : JPanel(BorderLayout()), OptionsPane.Option {
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val accountManager get() = AccountManager.getInstance()

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        add(getCenterComponent(), BorderLayout.CENTER)
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

        val title = FlatLabel()
        title.text = Application.getName()
        title.labelType = FlatLabel.LabelType.h1

        return FormBuilder.create().layout(layout).debug(false)
            .add(title).xyw(1, rows, 3).apply { rows += step }
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

    override fun getIcon(isSelected: Boolean): Icon {
        return Icons.user
    }

    override fun getTitle(): String {
        return I18n.getString("termora.settings.account")
    }

    override fun getJComponent(): JComponent {
        return this
    }

}
