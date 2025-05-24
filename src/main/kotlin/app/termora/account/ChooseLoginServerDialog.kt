package app.termora.account

import app.termora.*
import app.termora.Application.ohMyJson
import app.termora.OptionsPane.Companion.formMargin
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.db.DatabaseManager
import com.formdev.flatlaf.FlatClientProperties
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.JXHyperlink
import java.awt.Component
import java.awt.Dimension
import java.awt.Window
import java.awt.event.ItemEvent
import java.net.URI
import javax.swing.*
import kotlin.math.max

class ChooseLoginServerDialog(owner: Window) : DialogWrapper(owner) {
    var server: Server? = null
    private val serverComboBox = OutlineComboBox<Server>()

    init {
        isModal = true
        isResizable = true
        controlsVisible = false
        title = I18n.getString("termora.settings.account.choose-server")
        init()
        pack()
        size = Dimension(max(preferredSize.width, UIManager.getInt("Dialog.width") - 250), preferredSize.height)
        setLocationRelativeTo(owner)
    }

    override fun createCenterPanel(): JComponent {
        val layout = FormLayout(
            "left:pref, $formMargin, default:grow, $formMargin, pref",
            "pref, $formMargin"
        )

        var rows = 1
        val step = 2


        val singaporeServer = Server(I18n.getString("termora.settings.account.server-singapore"), "https://account.termora.app")
        val chinaServer = Server(I18n.getString("termora.settings.account.server-china"), "https://account.termora.cn")

        if (Application.isUnknownVersion()) {
            serverComboBox.addItem(Server("Localhost", "http://127.0.0.1:8080"))
        }

        serverComboBox.addItem(singaporeServer)
        serverComboBox.addItem(chinaServer)

        val properties = DatabaseManager.getInstance().properties
        val servers = (runCatching {
            ohMyJson.decodeFromString<List<Server>>(properties.getString("login-servers", "[]"))
        }.getOrNull() ?: emptyList()).toMutableList()
        for (server in servers) {
            serverComboBox.addItem(Server(server.name, server.server))
        }

        serverComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                if (value is Server) {
                    if (isSelected) {
                        return super.getListCellRendererComponent(
                            list,
                            "[${value.name}] ${value.server}",
                            index,
                            true,
                            cellHasFocus
                        )
                    }
                    val color = UIManager.getColor("textInactiveText")
                    return super.getListCellRendererComponent(
                        list,
                        "<html><font color=rgb(${color.red},${color.green},${color.blue})>[${value.name}]</font> ${value.server}</html>",
                        index,
                        false,
                        cellHasFocus
                    )
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            }
        }


        val dialog = this
        val newAction = object : AnAction(I18n.getString("termora.welcome.contextmenu.new")) {
            override fun actionPerformed(evt: AnActionEvent) {
                if (serverComboBox.selectedItem == singaporeServer || serverComboBox.selectedItem == chinaServer) {
                    val c = NewServerDialog(dialog)
                    c.isVisible = true
                    val server = c.server ?: return
                    serverComboBox.addItem(server)
                    serverComboBox.selectedItem = server
                    servers.add(server)
                    properties.putString("login-servers", ohMyJson.encodeToString(servers))
                } else {
                    if (OptionPane.showConfirmDialog(
                            dialog,
                            I18n.getString("termora.keymgr.delete-warning"),
                            I18n.getString("termora.remove"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE
                        ) == JOptionPane.YES_OPTION
                    ) {
                        val item = serverComboBox.selectedItem
                        serverComboBox.removeItem(item)
                        servers.removeIf { it == item }
                        properties.putString("login-servers", ohMyJson.encodeToString(servers))
                    }
                }
            }
        }
        val newServer = JXHyperlink(newAction)
        newServer.isFocusable = false
        serverComboBox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                if (serverComboBox.selectedItem == singaporeServer || serverComboBox.selectedItem == chinaServer) {
                    newAction.name = I18n.getString("termora.welcome.contextmenu.new")
                } else {
                    newAction.name = I18n.getString("termora.remove")
                }
            }
        }


        return FormBuilder.create().layout(layout).debug(false).padding("0dlu, $formMargin, 0dlu, $formMargin")
            .add("${I18n.getString("termora.settings.account.server")}:").xy(1, rows)
            .add(serverComboBox).xy(3, rows)
            .add(newServer).xy(5, rows).apply { rows += step }
            .build()
    }

    private class NewServerDialog(owner: Window) : DialogWrapper(owner) {
        private val nameTextField = OutlineTextField(128)
        private val serverTextField = OutlineTextField(256)
        var server: Server? = null

        init {
            isModal = true
            isResizable = false
            controlsVisible = false
            title = I18n.getString("termora.settings.account.new-server")
            init()
            pack()
            size = Dimension(max(preferredSize.width, UIManager.getInt("Dialog.width") - 320), preferredSize.height)
            setLocationRelativeTo(owner)
        }

        override fun createCenterPanel(): JComponent {
            val layout = FormLayout(
                "left:pref, $formMargin, default:grow, $formMargin, pref",
                "pref, $formMargin, pref, $formMargin"
            )

            var rows = 1
            val step = 2

            val deploy = JXHyperlink(object : AnAction(I18n.getString("termora.settings.account.deploy-server")) {
                override fun actionPerformed(evt: AnActionEvent) {
                    Application.browse(URI.create("https://github.com/TermoraDev/termora-backend"))
                }
            })
            deploy.isFocusable = false


            return FormBuilder.create().layout(layout).debug(false).padding("0dlu, $formMargin, 0dlu, $formMargin")
                .add("${I18n.getString("termora.new-host.general.name")}:").xy(1, rows)
                .add(nameTextField).xyw(3, rows, 3).apply { rows += step }
                .add("${I18n.getString("termora.settings.account.server")}:").xy(1, rows)
                .add(serverTextField).xy(3, rows)
                .add(deploy).xy(5, rows).apply { rows += step }
                .build()
        }

        override fun doOKAction() {
            if (nameTextField.text.isBlank()) {
                nameTextField.outline = FlatClientProperties.OUTLINE_ERROR
                nameTextField.requestFocusInWindow()
                return
            }
            val uri = runCatching { URI.create(serverTextField.text) }.getOrNull()
            val isHttp = uri != null && StringUtils.equalsAnyIgnoreCase(uri.scheme, "http", "https")
            if (serverTextField.text.isBlank() || isHttp.not()) {
                serverTextField.outline = FlatClientProperties.OUTLINE_ERROR
                serverTextField.requestFocusInWindow()
                return
            }

            server = Server(nameTextField.text.trim(), serverTextField.text.trim())
            super.doOKAction()
        }

        override fun doCancelAction() {
            server = null
            super.doCancelAction()
        }
    }

    override fun doOKAction() {
        server = serverComboBox.selectedItem as? Server
        super.doOKAction()
    }

    override fun doCancelAction() {
        server = null
        super.doCancelAction()
    }
}