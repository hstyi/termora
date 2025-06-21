package app.termora.transfer

import app.termora.I18n
import app.termora.OptionsPane.Companion.FORM_MARGIN
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.attribute.PosixFilePermission
import javax.swing.*
import kotlin.math.max

class PosixFilePermissionPanel(private val permissions: Set<PosixFilePermission>) : JPanel(BorderLayout()) {


    private val ownerRead = JCheckBox(I18n.getString("termora.transport.permissions.read"))
    private val ownerWrite = JCheckBox(I18n.getString("termora.transport.permissions.write"))
    private val ownerExecute = JCheckBox(I18n.getString("termora.transport.permissions.execute"))
    private val groupRead = JCheckBox(I18n.getString("termora.transport.permissions.read"))
    private val groupWrite = JCheckBox(I18n.getString("termora.transport.permissions.write"))
    private val groupExecute = JCheckBox(I18n.getString("termora.transport.permissions.execute"))
    private val otherRead = JCheckBox(I18n.getString("termora.transport.permissions.read"))
    private val otherWrite = JCheckBox(I18n.getString("termora.transport.permissions.write"))
    private val otherExecute = JCheckBox(I18n.getString("termora.transport.permissions.execute"))
    private val includeSubFolder = JCheckBox(I18n.getString("termora.transport.permissions.include-subfolder"))


    init {
        initView()
    }

    private fun initView() {
        ownerRead.isSelected = permissions.contains(PosixFilePermission.OWNER_READ)
        ownerWrite.isSelected = permissions.contains(PosixFilePermission.OWNER_WRITE)
        ownerExecute.isSelected = permissions.contains(PosixFilePermission.OWNER_EXECUTE)
        groupRead.isSelected = permissions.contains(PosixFilePermission.GROUP_READ)
        groupWrite.isSelected = permissions.contains(PosixFilePermission.GROUP_WRITE)
        groupExecute.isSelected = permissions.contains(PosixFilePermission.GROUP_EXECUTE)
        otherRead.isSelected = permissions.contains(PosixFilePermission.OTHERS_READ)
        otherWrite.isSelected = permissions.contains(PosixFilePermission.OTHERS_WRITE)
        otherExecute.isSelected = permissions.contains(PosixFilePermission.OTHERS_EXECUTE)

        ownerRead.isFocusable = false
        ownerWrite.isFocusable = false
        ownerExecute.isFocusable = false
        groupRead.isFocusable = false
        groupWrite.isFocusable = false
        groupExecute.isFocusable = false
        otherRead.isFocusable = false
        otherWrite.isFocusable = false
        otherExecute.isFocusable = false
        includeSubFolder.isFocusable = false

        add(createCenterPanel(), BorderLayout.CENTER)

        preferredSize = Dimension(
            max(preferredSize.width, UIManager.getInt("Dialog.width") - 350),
            preferredSize.height
        )

    }

    private fun createCenterPanel(): JComponent {
        val formMargin = FORM_MARGIN
        val layout = FormLayout(
            "default:grow, $formMargin, default:grow, $formMargin, default:grow",
            "pref, $formMargin, pref, $formMargin, pref"
        )

        val builder = FormBuilder.create().layout(layout).debug(false)

        builder.add("${I18n.getString("termora.transport.permissions.file-folder-permissions")}:").xyw(1, 1, 5)

        val ownerBox = Box.createVerticalBox()
        ownerBox.add(ownerRead)
        ownerBox.add(ownerWrite)
        ownerBox.add(ownerExecute)
        ownerBox.border = BorderFactory.createTitledBorder(I18n.getString("termora.transport.permissions.owner"))
        builder.add(ownerBox).xy(1, 3)

        val groupBox = Box.createVerticalBox()
        groupBox.add(groupRead)
        groupBox.add(groupWrite)
        groupBox.add(groupExecute)
        groupBox.border = BorderFactory.createTitledBorder(I18n.getString("termora.transport.permissions.group"))
        builder.add(groupBox).xy(3, 3)

        val otherBox = Box.createVerticalBox()
        otherBox.add(otherRead)
        otherBox.add(otherWrite)
        otherBox.add(otherExecute)
        otherBox.border = BorderFactory.createTitledBorder(I18n.getString("termora.transport.permissions.others"))
        builder.add(otherBox).xy(5, 3)

        builder.add(includeSubFolder).xyw(1, 5, 5)

        return builder.build()
    }


    fun isIncludeSubdirectories(): Boolean {
        return includeSubFolder.isSelected
    }

    fun getPermissions(): Set<PosixFilePermission> {

        val permissions = mutableSetOf<PosixFilePermission>()
        if (ownerRead.isSelected) {
            permissions.add(PosixFilePermission.OWNER_READ)
        }
        if (ownerWrite.isSelected) {
            permissions.add(PosixFilePermission.OWNER_WRITE)
        }
        if (ownerExecute.isSelected) {
            permissions.add(PosixFilePermission.OWNER_EXECUTE)
        }
        if (groupRead.isSelected) {
            permissions.add(PosixFilePermission.GROUP_READ)
        }
        if (groupWrite.isSelected) {
            permissions.add(PosixFilePermission.GROUP_WRITE)
        }
        if (groupExecute.isSelected) {
            permissions.add(PosixFilePermission.GROUP_EXECUTE)
        }
        if (otherRead.isSelected) {
            permissions.add(PosixFilePermission.OTHERS_READ)
        }
        if (otherWrite.isSelected) {
            permissions.add(PosixFilePermission.OTHERS_WRITE)
        }
        if (otherExecute.isSelected) {
            permissions.add(PosixFilePermission.OTHERS_EXECUTE)
        }

        return permissions
    }
}