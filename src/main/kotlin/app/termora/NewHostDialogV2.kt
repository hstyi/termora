package app.termora

import app.termora.Application.ohMyJson
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
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
    private var currentCard: LazyPanel? = null
    private val buttonGroup = mutableListOf<JToggleButton>()
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
        for (extension in extensions) {
            val protocol = extension.getProtocolProvider().getProtocol()
            val icon = FlatSVGIcon(
                extension.getProtocolProvider().getIcon().name,
                22, 22, extension.javaClass.classLoader
            )
            val lazyPanel = LazyPanel(extension)
            val button = JToggleButton(protocol, icon).apply { buttonGroup.add(this) }
            button.setVerticalTextPosition(SwingConstants.BOTTOM)
            button.setHorizontalTextPosition(SwingConstants.CENTER)
            button.border = BorderFactory.createCompoundBorder(
                FlatButtonBorder(),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)
            )
            button.addActionListener { show(protocol, lazyPanel, button) }

            cardPanel.add(lazyPanel, protocol)

            toolbar.add(button)

            if (extension != extensions.last()) {
                toolbar.add(Box.createHorizontalStrut(6))
            }

            if (editHost == null) {
                if (extension == extensions.first()) {
                    show(protocol, lazyPanel, button)
                }
            } else {
                if (StringUtils.equalsIgnoreCase(editHost.protocol, protocol)) {
                    show(protocol, lazyPanel, button)
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

    private fun show(name: String, card: LazyPanel, button: JToggleButton) {
        card.load()

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

    private inner class LazyPanel(private val extension: ProtocolHostPanelExtension) : JPanel(BorderLayout()) {
        private var isLoaded = false
        val panel by lazy { extension.createProtocolHostPanel() }

        fun load() {
            if (isLoaded) return
            isLoaded = true
            if (editHost != null) panel.setHost(editHost)
            add(panel, BorderLayout.CENTER)
        }


        fun onBeforeHidden() {
            if (isLoaded) panel.onBeforeHidden()
        }

        fun onBeforeShown() {
            if (isLoaded) panel.onBeforeShown()
        }

        fun onShown() {
            if (isLoaded) panel.onShown()
        }

        fun onHidden() {
            if (isLoaded) panel.onHidden()
        }
    }

    override fun doOKAction() {
        val card = currentCard ?: return
        val panel = card.panel
        if (panel.validateFields().not()) return
        var host = panel.getHost()
        println(ohMyJson.encodeToString(host))

        if (editHost != null) host = host.copy(id = editHost.id)
        this.host = host
        println(ohMyJson.encodeToString(host))
        super.doOKAction()
    }


}