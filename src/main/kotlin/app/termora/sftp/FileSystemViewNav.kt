package app.termora.sftp

import app.termora.Icons
import app.termora.assertEventDispatchThread
import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.FlatTextField
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Graphics
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.nio.file.FileSystem
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.filechooser.FileSystemView
import kotlin.io.path.absolutePathString

class FileSystemViewNav(
    private val fileSystem: FileSystem,
    private val homeDirectory: Path
) : JPanel(BorderLayout()) {

    companion object {
        private const val PATH = "path"
        private val log = LoggerFactory.getLogger(FileSystemViewNav::class.java)
    }

    private val fileSystemView = FileSystemView.getFileSystemView()
    private val textField = MyFlatTextField()
    private var popupLastTime = 0L
    private val history = linkedSetOf<String>()
    private val layeredPane = LayeredPane()
    private val downBtn = JButton(Icons.chevronDown)
    private val comboBox = object : JComboBox<Path>() {
        override fun getLocationOnScreen(): Point {
            val point = super.getLocationOnScreen()
            point.y -= 1
            return point
        }
    }

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {

        comboBox.isEnabled = false
        comboBox.putClientProperty("JComboBox.isTableCellEditor", true)

        textField.leadingIcon = NativeFileIcons.getFolderIcon()
        textField.trailingComponent = downBtn
        textField.text = homeDirectory.absolutePathString()
        textField.putClientProperty(PATH, homeDirectory)

        downBtn.putClientProperty(
            FlatClientProperties.STYLE,
            mapOf(
                "toolbar.hoverBackground" to UIManager.getColor("Button.background"),
                "toolbar.pressedBackground" to UIManager.getColor("Button.background"),
            )
        )

        comboBox.renderer = object : DefaultListCellRenderer() {
            private val indentIcon = IndentIcon()
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val c = super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus
                )

                indentIcon.depth = 0
                indentIcon.icon = NativeFileIcons.getFolderIcon()

                icon = indentIcon
                return c
            }
        }

        layeredPane.add(comboBox, JLayeredPane.DEFAULT_LAYER as Any)
        layeredPane.add(textField, JLayeredPane.PALETTE_LAYER as Any)
        add(layeredPane, BorderLayout.CENTER)


        if (fileSystem.isWindows()) {
            try {
                for (root in fileSystemView.roots) {
                    history.add(root.absolutePath)
                }
                for (rootDirectory in fileSystem.rootDirectories) {
                    history.add(rootDirectory.absolutePathString())
                }
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }
    }

    private fun initEvents() {

        val itemListener = ItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                val item = comboBox.selectedItem
                if (item is Path) {
                    changeSelectedPath(item)
                }
            }
        }

        comboBox.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
                comboBox.addItemListener(itemListener)
            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
                popupLastTime = System.currentTimeMillis()
                comboBox.removeItemListener(itemListener)
                comboBox.isEnabled = false
                textField.requestFocusInWindow()
            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {
            }

        })

        // 监听 Action
        addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val text = textField.text.trim()
                if (text.isBlank()) return
                if (history.contains(text)) return
                history.add(text)
            }
        })

        downBtn.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (System.currentTimeMillis() - popupLastTime < 250) return
                comboBox.isEnabled = true
                comboBox.requestFocusInWindow()
                showComboBoxPopup()
            }
        })

        textField.addActionListener(object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val name = textField.text.trim()
                if (name.isBlank()) return
                try {
                    changeSelectedPath(fileSystem.getPath(name))
                } catch (e: Exception) {
                    if (log.isErrorEnabled) {
                        log.error(e.message, e)
                    }
                }
            }
        })
    }

    private fun showComboBoxPopup() {

        comboBox.removeAllItems()

        for (text in history) {
            val path = fileSystem.getPath(text)
            comboBox.addItem(path)
            if (text == textField.text) {
                comboBox.selectedItem = path
            }
        }

        comboBox.showPopup()
    }

    fun addActionListener(l: ActionListener) {
        listenerList.add(ActionListener::class.java, l)
    }

    class IndentIcon : Icon {
        val space = 10
        var depth: Int = 0
        var icon = NativeFileIcons.getFolderIcon()

        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
            if (c.componentOrientation.isLeftToRight) {
                icon.paintIcon(c, g, x + depth * space, y)
            } else {
                icon.paintIcon(c, g, x, y)
            }
        }

        override fun getIconWidth(): Int {
            return icon.iconWidth + depth * space
        }

        override fun getIconHeight(): Int {
            return icon.iconHeight
        }
    }

    fun getSelectedPath(): Path {
        return textField.getClientProperty(PATH) as Path
    }

    fun changeSelectedPath(path: Path) {
        assertEventDispatchThread()

        textField.text = path.absolutePathString()
        textField.putClientProperty(PATH, path)

        for (listener in listenerList.getListeners(ActionListener::class.java)) {
            listener.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, StringUtils.EMPTY))
        }

    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun updateUI() {
        super.updateUI()
        downBtn?.putClientProperty(
            FlatClientProperties.STYLE,
            mapOf(
                "toolbar.hoverBackground" to UIManager.getColor("Button.background"),
                "toolbar.pressedBackground" to UIManager.getColor("Button.background"),
            )
        )
    }

    class MyFlatTextField : FlatTextField() {
        public override fun fireActionPerformed() {
            super.fireActionPerformed()
        }
    }


    private class LayeredPane : JLayeredPane() {
        override fun doLayout() {
            synchronized(treeLock) {
                for (c in components) {
                    c.setBounds(0, 0, width, height)
                }
            }
        }
    }

}