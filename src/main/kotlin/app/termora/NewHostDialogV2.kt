package app.termora

import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.protocol.ProtocolHostPanel
import app.termora.protocol.ProtocolHostPanelExtension
import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.ui.FlatButtonBorder
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Window
import javax.swing.*

class NewHostDialogV2(owner: Window, private val editHost: Host? = null) : DialogWrapper(owner) {

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)
    private val buttonGroup = mutableListOf<JToggleButton>()
    private var currentCard: ProtocolHostPanel? = null
    var host: Host? = null
        private set

    init {

        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = true
        title = I18n.getString("termora.new-host.title")

        setLocationRelativeTo(owner)

        init()
    }


    override fun addNotify() {
        super.addNotify()

        controlsVisible = false
    }

    override fun createCenterPanel(): JComponent {
        val toolbar = FlatToolBar()
        val panel = JPanel(BorderLayout())

        toolbar.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 1, 0, DynamicColor.BorderColor),
            BorderFactory.createEmptyBorder(4, 0, 4, 0)
        )
        panel.add(toolbar, BorderLayout.NORTH)
        panel.add(cardPanel, BorderLayout.CENTER)

        toolbar.add(Box.createHorizontalGlue())

        val extensions = ProtocolHostPanelExtension.extensions
        for ((index, extension) in extensions.withIndex()) {
            val protocol = extension.getProtocolProvider().getProtocol()
            val icon = FlatSVGIcon(
                extension.getProtocolProvider().getIcon().name,
                22, 22, extension.javaClass.classLoader
            )
            val hostPanel = extension.createProtocolHostPanel()
            val button = JToggleButton(protocol, icon).apply { buttonGroup.add(this) }
            button.setVerticalTextPosition(SwingConstants.BOTTOM)
            button.setHorizontalTextPosition(SwingConstants.CENTER)
            button.border = BorderFactory.createCompoundBorder(
                FlatButtonBorder(),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)
            )
            button.addActionListener { show(protocol, hostPanel, button) }

            cardPanel.add(hostPanel, protocol)

            toolbar.add(button)

            if (extension != extensions.last()) {
                toolbar.add(Box.createHorizontalStrut(6))
            }

            if (editHost == null) {
                if (index == 0) {
                    show(protocol, hostPanel, button)
                }
            } else {
                if (StringUtils.equalsIgnoreCase(editHost.protocol, protocol)) {
                    show(protocol, hostPanel, button)
                    currentCard?.setHost(editHost)
                }
            }

        }

        if (editHost != null && currentCard == null) {
            SwingUtilities.invokeLater {
                OptionPane.showMessageDialog(
                    this,
                    "Protocol ${editHost.protocol} not supported",
                    messageType = JOptionPane.ERROR_MESSAGE
                )
                doCancelAction()
            }
        }

        toolbar.add(Box.createHorizontalGlue())

        return panel
    }

    private fun show(name: String, card: ProtocolHostPanel, button: JToggleButton) {
        currentCard?.onBeforeHidden()
        card.onBeforeShown()
        cardLayout.show(cardPanel, name)
        currentCard?.onHidden()
        card.onShown()

        currentCard = card

        buttonGroup.forEach { it.isSelected = false }
        button.isSelected = true
    }

    override fun createActions(): List<AbstractAction> {
        return listOf(createOkAction(), createTestConnectionAction(), CancelAction())
    }

    private fun createTestConnectionAction(): AbstractAction {
        return object : AnAction(I18n.getString("termora.new-host.test-connection")) {
            override fun actionPerformed(evt: AnActionEvent) {

            }
        }
    }

    override fun doOKAction() {
        val panel = currentCard ?: return
        if (panel.validateFields().not()) return
        var host = panel.getHost()

        if (editHost != null) {
            host = editHost.copy(
                name = host.name,
                protocol = host.protocol,
                host = host.host,
                port = host.port,
                username = host.username,
                authentication = host.authentication,
                proxy = host.proxy,
                remark = host.remark,
                options = host.options,
                tunnelings = host.tunnelings,
            )
        }

        this.host = host

        super.doOKAction()
    }


}