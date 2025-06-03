package app.termora.plugin.internal.plugin

import app.termora.*
import app.termora.account.Account
import app.termora.account.AccountExtension
import app.termora.account.AccountManager
import app.termora.plugin.PluginDescriptor
import app.termora.plugin.PluginManager
import app.termora.plugin.PluginOrigin
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import com.formdev.flatlaf.extras.FlatSVGIcon
import org.apache.commons.io.FileUtils
import org.jdesktop.swingx.JXLabel
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class PluginOption : JPanel(BorderLayout()), OptionsPane.Option, Disposable, AccountExtension {
    private val pluginManager = PluginManager.getInstance()
    private val owner get() = SwingUtilities.getWindowAncestor(this)
    private val installButtons = mutableListOf<JButton>()

    companion object {
        private val installed = mutableSetOf<String>()
        private val uninstalled = mutableSetOf<String>()
    }

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        val panel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 8))
        val scrollPane = JScrollPane(panel)

        val installedPlugins = pluginManager.getLoadedPluginDescriptor()
            .sortedBy { it.plugin.getName().length }

        for (e in installedPlugins) {
            if (e.origin == PluginOrigin.Internal) continue
            panel.add(createUninstallPanel(e))
            panel.add(JToolBar.Separator())
        }

        for (e in installedPlugins) {
            if (e.origin == PluginOrigin.Internal) continue
            panel.add(createInstallPanel(e))
            panel.add(JToolBar.Separator())
        }

        if (Application.isUnknownVersion()) {
            for (e in installedPlugins) {
                if (e.origin != PluginOrigin.Internal) continue
                panel.add(createUninstallPanel(e))
                panel.add(JToolBar.Separator())
            }
        }


        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.verticalScrollBar.unitIncrement = 16
        scrollPane.horizontalScrollBar.unitIncrement = 16
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun createUninstallPanel(e: PluginDescriptor): JPanel {
        val plugin = e.plugin
        val pluginBox = JPanel()
        pluginBox.setLayout(BoxLayout(pluginBox, BoxLayout.X_AXIS))
        pluginBox.add(JLabel(FlatSVGIcon(plugin.getIcon().name, 32, 32, e.plugin.javaClass.classLoader)))
        pluginBox.add(Box.createHorizontalStrut(8))

        val infoBox = Box.createVerticalBox()
        infoBox.add(JLabel("<html><b>${plugin.getName()}</b>&nbsp;&nbsp;${e.version}</html>"))
        infoBox.add(Box.createVerticalStrut(4))
        val descriptionLabel = JXLabel(plugin.getDescription())
            .apply { foreground = DynamicColor("textInactiveText") }
        descriptionLabel.preferredSize = Dimension(0, descriptionLabel.preferredSize.height)
        descriptionLabel.toolTipText = plugin.getDescription()

        infoBox.add(descriptionLabel)
        pluginBox.add(infoBox)
        pluginBox.add(Box.createHorizontalGlue())
        pluginBox.add(Box.createHorizontalStrut(8))

        val uninstallButton = JButton(I18n.getString("termora.settings.plugin.uninstall"))
        if (uninstalled.contains(plugin.getName())) {
            uninstallButton.text = I18n.getString("termora.settings.plugin.uninstalled")
        }
        uninstallButton.isFocusable = false
        uninstallButton.isEnabled = e.origin == PluginOrigin.External
                && uninstalled.contains(plugin.getName()).not()

        if (e.origin == PluginOrigin.System) {
            uninstallButton.toolTipText = I18n.getString("termora.settings.plugin.cannot-uninstall")
            pluginBox.add(uninstallButton)
        } else if (e.origin == PluginOrigin.External) {
            uninstallButton.addActionListener { uninstall(uninstallButton, e) }
            pluginBox.add(uninstallButton)
        }

        pluginBox.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)

        return pluginBox
    }

    private fun createInstallPanel(e: PluginDescriptor): JPanel {
        val plugin = e.plugin
        val pluginBox = JPanel()
        pluginBox.setLayout(BoxLayout(pluginBox, BoxLayout.X_AXIS))
        pluginBox.add(JLabel(FlatSVGIcon(plugin.getIcon().name, 32, 32, e.plugin.javaClass.classLoader)))
        pluginBox.add(Box.createHorizontalStrut(8))

        val infoBox = Box.createVerticalBox()
        infoBox.add(JLabel("<html><b>${plugin.getName()}</b>&nbsp;&nbsp;${e.version}</html>"))
        infoBox.add(Box.createVerticalStrut(4))
        val descriptionLabel = JXLabel(plugin.getDescription())
            .apply { foreground = DynamicColor("textInactiveText") }
        descriptionLabel.preferredSize = Dimension(0, descriptionLabel.preferredSize.height)
        descriptionLabel.toolTipText = plugin.getDescription()

        infoBox.add(descriptionLabel)
        pluginBox.add(infoBox)
        pluginBox.add(Box.createHorizontalGlue())
        pluginBox.add(Box.createHorizontalStrut(8))

        val installButton = JButton(I18n.getString("termora.settings.plugin.install"))
        installButton.isFocusable = false
        installButton.isEnabled = installed.contains(plugin.getName()).not()
        if (AccountManager.getInstance().isFreePlan()) {
            installButton.icon = Icons.locked
            installButton.addActionListener { install(installButton, e) }
        } else {
            installButton.text = I18n.getString("termora.settings.plugin.installed")
        }

        installButtons.add(installButton)

        pluginBox.add(installButton)
        pluginBox.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)

        return pluginBox
    }

    private fun uninstall(button: JButton, descriptor: PluginDescriptor) {
        if (descriptor.origin != PluginOrigin.External || uninstalled.contains(descriptor.plugin.getName())) return
        val file = descriptor.path ?: return
        val option = OptionPane.showConfirmDialog(
            owner,
            I18n.getString("termora.settings.plugin.uninstall-confirm", descriptor.plugin.getName()),
            optionType = JOptionPane.OK_CANCEL_OPTION,
            messageType = JOptionPane.QUESTION_MESSAGE
        )
        if (option != JOptionPane.OK_OPTION) return

        val deletedFile = FileUtils.getFile(file, "deleted")
        if (deletedFile.exists()) return
        if (deletedFile.createNewFile()) {
            uninstalled.add(descriptor.plugin.getName())
            button.text = I18n.getString("termora.settings.plugin.uninstalled")
            button.isEnabled = false
            TermoraRestarter.getInstance().scheduleRestart(owner)
        } else {
            OptionPane.showMessageDialog(
                owner,
                I18n.getString("termora.settings.plugin.uninstall-failed"),
                messageType = JOptionPane.ERROR_MESSAGE
            )
        }

    }

    private fun install(button: JButton, descriptor: PluginDescriptor) {
        if (AccountManager.getInstance().isFreePlan()) {
            val option = OptionPane.showConfirmDialog(
                owner,
                I18n.getString("termora.settings.plugin.install-subscription-confirm", descriptor.plugin.getName()),
                options = arrayOf(
                    I18n.getString("termora.settings.account.upgrade"),
                    I18n.getString("termora.cancel")
                ),
                optionType = JOptionPane.OK_CANCEL_OPTION,
                messageType = JOptionPane.WARNING_MESSAGE,
                initialValue = I18n.getString("termora.settings.account.upgrade")
            )
            if (option == JOptionPane.OK_OPTION) {
                return
            }
            return
        }

        button.isEnabled = false
        button.icon = null
        button.text = I18n.getString("termora.settings.plugin.installed")
        installed.add(descriptor.plugin.getName())

        TermoraRestarter.getInstance().scheduleRestart(owner)

    }


    private fun initEvents() {
        DynamicExtensionHandler.getInstance().register(AccountExtension::class.java, this)
    }

    override fun getIcon(isSelected: Boolean): Icon {
        return Icons.plugin
    }

    override fun getTitle(): String {
        return I18n.getString("termora.settings.plugin")
    }

    override fun getJComponent(): JComponent {
        return this
    }

    override fun dispose() {
        DynamicExtensionHandler.getInstance().unregister(this)
    }

    override fun onAccountChanged(oldAccount: Account, newAccount: Account) {
        val isFreePlan = AccountManager.getInstance().isFreePlan()
        for (button in installButtons) {
            button.icon = if (isFreePlan) Icons.locked else null
        }
    }

    override fun getIdentifier(): String {
        return "Plugin"
    }

    override fun getAnchor(): OptionsPane.Anchor {
        return OptionsPane.Anchor.Before("About")
    }
}
