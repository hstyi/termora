package app.termora.plugins.editor

import app.termora.Disposable
import app.termora.Disposer
import app.termora.EnableManager
import app.termora.OptionPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.io.path.absolutePathString
import kotlin.io.path.name
import kotlin.math.max

class EditorFrame(private val file: Path, private val owner: Window, private val disposable: Disposable) : JFrame() {
    private val enableManager get() = EnableManager.getInstance()
    private val disposed = AtomicBoolean()
    private val filepath = File(file.absolutePathString())
    private val frame get() = this
    private val editorPanel = EditorPanel(this, filepath)

    init {
        initView()
        initEvent()
    }

    private fun initEvent() {

        Disposer.register(disposable, object : Disposable {
            override fun dispose() {
                if (disposed.compareAndSet(false, true)) frame.dispose()
            }
        })

        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                if (disposed.compareAndSet(false, true)) Disposer.dispose(disposable)
                enableManager.setFlag("Plugins.editor.dialog.width", width)
                enableManager.setFlag("Plugins.editor.dialog.height", height)
                enableManager.setFlag("Plugins.editor.dialog.extendedState", extendedState)
            }

            override fun windowClosing(e: WindowEvent?) {
                if (editorPanel.changes()) {
                    if (OptionPane.showConfirmDialog(
                            frame,
                            EditorI18n.getString("termora.plugins.editor.not-save"),
                            optionType = JOptionPane.OK_CANCEL_OPTION,
                        ) == JOptionPane.OK_OPTION
                    ) {
                        frame.dispose()
                    }
                } else {
                    frame.dispose()
                }
            }
        })

    }

    private fun initView() {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        val state = enableManager.getFlag("Plugins.editor.dialog.extendedState", 0)

        if ((state and MAXIMIZED_BOTH) == MAXIMIZED_BOTH) {
            frame.setLocationRelativeTo(null)
            frame.extendedState = state
        } else {
            val mySize = size
            mySize.width = max(enableManager.getFlag("Plugins.editor.dialog.width", mySize.width), mySize.width)
            mySize.height = max(enableManager.getFlag("Plugins.editor.dialog.height", mySize.height), mySize.height)
            size = mySize
            setLocationRelativeTo(owner)
        }

        title = file.name
        iconImages = owner.iconImages
        defaultCloseOperation = DO_NOTHING_ON_CLOSE

        rootPane.contentPane.layout = BorderLayout()
        rootPane.contentPane.add(editorPanel, BorderLayout.CENTER)

    }
}