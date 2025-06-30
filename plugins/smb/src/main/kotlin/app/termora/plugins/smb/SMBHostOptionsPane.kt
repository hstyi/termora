package app.termora.plugins.smb

import app.termora.*
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.ui.FlatTextBorder
import com.hierynomus.smbj.SMBClient
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.lang3.StringUtils
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class SMBHostOptionsPane : OptionsPane() {
    private val generalOption = GeneralOption()
    private val sftpOption = SFTPOption()

    init {
        addOption(generalOption)
        addOption(sftpOption)

    }


    fun getHost(): Host {
        val name = generalOption.nameTextField.text
        val protocol = SMBProtocolProvider.PROTOCOL
        val host = generalOption.hostTextField.text
        val port = generalOption.portTextField.value as Int
        var authentication = Authentication.Companion.No
        val authenticationType = AuthenticationType.Password

        authentication = authentication.copy(
            type = authenticationType,
            password = String(generalOption.passwordTextField.password)
        )


        val options = Options.Default.copy(
            sftpDefaultDirectory = sftpOption.defaultDirectoryField.text,
            extras = mutableMapOf(
                "smb.share" to generalOption.shareTextField.text,
            )
        )

        return Host(
            name = name,
            protocol = protocol,
            host = host,
            port = port,
            username = generalOption.usernameTextField.selectedItem as String,
            authentication = authentication,
            sort = System.currentTimeMillis(),
            remark = generalOption.remarkTextArea.text,
            options = options,
        )
    }

    fun setHost(host: Host) {
        generalOption.nameTextField.text = host.name
        generalOption.usernameTextField.selectedItem = host.username
        generalOption.hostTextField.text = host.host
        generalOption.portTextField.value = host.port
        generalOption.remarkTextArea.text = host.remark
        generalOption.passwordTextField.text = host.authentication.password
        generalOption.shareTextField.text = host.options.extras["smb.share"] ?: StringUtils.EMPTY

        sftpOption.defaultDirectoryField.text = host.options.sftpDefaultDirectory
    }

    fun validateFields(): Boolean {

        // general
        if (validateField(generalOption.nameTextField)
            || validateField(generalOption.hostTextField)
            || validateField(generalOption.shareTextField)
        ) {
            return false
        }

        val username = generalOption.usernameTextField.selectedItem as String?
        if (username.isNullOrBlank()) {
            setOutlineError(generalOption.usernameTextField)
            return false
        }



        return true
    }

    /**
     * 返回 true 表示有错误
     */
    private fun validateField(textField: JTextField): Boolean {
        if (textField.isEnabled && textField.text.isBlank()) {
            setOutlineError(textField)
            return true
        }
        return false
    }

    private fun setOutlineError(textField: JComponent) {
        selectOptionJComponent(textField)
        textField.putClientProperty(FlatClientProperties.OUTLINE, FlatClientProperties.OUTLINE_ERROR)
        textField.requestFocusInWindow()
    }


    private inner class GeneralOption : JPanel(BorderLayout()), Option {
        val portTextField = PortSpinner(SMBClient.DEFAULT_PORT)
        val nameTextField = OutlineTextField(128)
        val shareTextField = OutlineTextField(256)
        val usernameTextField = OutlineComboBox<String>()
        val hostTextField = OutlineTextField(255)
        val passwordTextField = OutlinePasswordField(255)
        val remarkTextArea = FixedLengthTextArea(512)

        init {
            initView()
            initEvents()
        }

        private fun initView() {
            usernameTextField.isEditable = true
            usernameTextField.addItem("Guest")
            usernameTextField.addItem("Anonymous")

            add(getCenterComponent(), BorderLayout.CENTER)

        }

        private fun initEvents() {


            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    SwingUtilities.invokeLater { nameTextField.requestFocusInWindow() }
                    removeComponentListener(this)
                }
            })
        }


        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.settings
        }

        override fun getTitle(): String {
            return I18n.getString("termora.new-host.general")
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $FORM_MARGIN, default:grow, $FORM_MARGIN, pref, $FORM_MARGIN, default",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
            )
            remarkTextArea.setFocusTraversalKeys(
                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
            )
            remarkTextArea.setFocusTraversalKeys(
                KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS)
            )

            remarkTextArea.rows = 8
            remarkTextArea.lineWrap = true
            remarkTextArea.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

            var rows = 1
            val step = 2
            val panel = FormBuilder.create().layout(layout)
                .add("${I18n.getString("termora.new-host.general.name")}:").xy(1, rows)
                .add(nameTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.host")}:").xy(1, rows)
                .add(hostTextField).xy(3, rows)
                .add("${I18n.getString("termora.new-host.general.port")}:").xy(5, rows)
                .add(portTextField).xy(7, rows).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.username")}:").xy(1, rows)
                .add(usernameTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.password")}:").xy(1, rows)
                .add(passwordTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${SMBI18n.getString("termora.plugins.smb.share")}:").xy(1, rows)
                .add(shareTextField).xyw(3, rows, 5).apply { rows += step }

                .add("${I18n.getString("termora.new-host.general.remark")}:").xy(1, rows)
                .add(JScrollPane(remarkTextArea).apply { border = FlatTextBorder() })
                .xyw(3, rows, 5).apply { rows += step }

                .build()


            return panel
        }

    }


    private inner class SFTPOption : JPanel(BorderLayout()), Option {
        val defaultDirectoryField = OutlineTextField(255)


        init {
            initView()
            initEvents()
        }

        private fun initView() {
            add(getCenterComponent(), BorderLayout.CENTER)
        }

        private fun initEvents() {

        }


        override fun getIcon(isSelected: Boolean): Icon {
            return Icons.folder
        }

        override fun getTitle(): String {
            return I18n.getString("termora.transport.sftp")
        }

        override fun getJComponent(): JComponent {
            return this
        }

        private fun getCenterComponent(): JComponent {
            val layout = FormLayout(
                "left:pref, $FORM_MARGIN, default:grow",
                "pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref, $FORM_MARGIN, pref"
            )

            var rows = 1
            val step = 2
            val panel = FormBuilder.create().layout(layout)
                .add("${I18n.getString("termora.settings.sftp.default-directory")}:").xy(1, rows)
                .add(defaultDirectoryField).xy(3, rows).apply { rows += step }
                .build()


            return panel
        }
    }


}