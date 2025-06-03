package app.termora.plugins.bg

import app.termora.*
import app.termora.OptionsPane.Companion.formMargin
import app.termora.db.DatabaseManager
import app.termora.nv.FileChooser
import com.formdev.flatlaf.extras.components.FlatButton
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.io.File
import java.nio.file.StandardCopyOption
import javax.swing.*
import javax.swing.event.DocumentEvent

class BackgroundOption : JPanel(BorderLayout()), OptionsPane.Option {
    companion object {
        private val log = LoggerFactory.getLogger(BackgroundOption::class.java)
    }

    private val owner get() = SwingUtilities.getWindowAncestor(this)

    val backgroundImageTextField = OutlineTextField()

    private val appearance get() = DatabaseManager.getInstance().appearance
    private val backgroundButton = JButton(Icons.folder)
    private val backgroundClearButton = FlatButton()


    init {
        initView()
        initEvents()
    }

    private fun initView() {

        backgroundImageTextField.isEditable = false
        backgroundImageTextField.trailingComponent = backgroundButton
        backgroundImageTextField.text = FilenameUtils.getName(appearance.backgroundImage)
        backgroundImageTextField.document.addDocumentListener(object : DocumentAdaptor() {
            override fun changedUpdate(e: DocumentEvent) {
                backgroundClearButton.isEnabled = backgroundImageTextField.text.isNotBlank()
            }
        })

        backgroundClearButton.isFocusable = false
        backgroundClearButton.isEnabled = backgroundImageTextField.text.isNotBlank()
        backgroundClearButton.icon = Icons.delete
        backgroundClearButton.buttonType = FlatButton.ButtonType.toolBarButton

        add(getFormPanel(), BorderLayout.CENTER)
    }

    private fun initEvents() {
        backgroundButton.addActionListener {
            val chooser = FileChooser()
            chooser.osxAllowedFileTypes = listOf("png", "jpg", "jpeg")
            chooser.allowsMultiSelection = false
            chooser.win32Filters.add(Pair("Image files", listOf("png", "jpg", "jpeg")))
            chooser.fileSelectionMode = JFileChooser.FILES_ONLY
            chooser.showOpenDialog(owner).thenAccept {
                if (it.isNotEmpty()) {
                    onSelectedBackgroundImage(it.first())
                }
            }
        }

        backgroundClearButton.addActionListener {
            BackgroundManager.getInstance().clearBackgroundImage()
            backgroundImageTextField.text = StringUtils.EMPTY
        }
    }

    private fun onSelectedBackgroundImage(file: File) {
        try {
            val destFile = FileUtils.getFile(Application.getBaseDataDir(), "background", file.name)
            FileUtils.forceMkdirParent(destFile)
            FileUtils.deleteQuietly(destFile)
            FileUtils.copyFile(file, destFile, StandardCopyOption.REPLACE_EXISTING)
            backgroundImageTextField.text = destFile.name
            BackgroundManager.getInstance().setBackgroundImage(destFile)
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
            SwingUtilities.invokeLater {
                OptionPane.showMessageDialog(
                    owner,
                    ExceptionUtils.getRootCauseMessage(e),
                    messageType = JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    override fun getIcon(isSelected: Boolean): Icon {
        return Icons.image
    }

    override fun getTitle(): String {
        return I18n.getString("termora.settings.appearance.background-image")
    }

    override fun getJComponent(): JComponent {
        return this
    }


    private fun getFormPanel(): JPanel {
        val layout = FormLayout(
            "left:pref, $formMargin, default:grow, $formMargin, default, default:grow",
            "pref"
        )

        var rows = 1
        val step = 2
        val builder = FormBuilder.create().layout(layout)
        val bgClearBox = Box.createHorizontalBox()
        bgClearBox.add(backgroundClearButton)
        builder.add("${I18n.getString("termora.settings.appearance.background-image")}:").xy(1, rows)
            .add(backgroundImageTextField).xy(3, rows)
            .add(bgClearBox).xy(5, rows)
            .apply { rows += step }

        return builder.build()
    }


}