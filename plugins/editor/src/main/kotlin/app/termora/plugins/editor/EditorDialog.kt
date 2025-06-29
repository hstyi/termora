package app.termora.plugins.editor

import app.termora.DialogWrapper
import app.termora.Disposable
import app.termora.Disposer
import app.termora.OptionPane
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.UIManager
import kotlin.io.path.absolutePathString
import kotlin.io.path.name


class EditorDialog(file: Path, owner: Window, private val myDisposable: Disposable) : DialogWrapper(null) {

    private val filename = file.name
    private val filepath = File(file.absolutePathString())
    private val editorPanel = EditorPanel(this, filepath)
    private val disposed = AtomicBoolean()

    init {
        size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        isModal = false
        controlsVisible = true
        isResizable = true
        title = filename
        iconImages = owner.iconImages
        escapeDispose = false
        defaultCloseOperation = DO_NOTHING_ON_CLOSE

        initEvents()

        setLocationRelativeTo(owner)

        init()
    }


    private fun initEvents() {

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                if (disposed.compareAndSet(false, true)) {
                    doCancelAction()
                }
            }
        })

        Disposer.register(myDisposable, object : Disposable {
            override fun dispose() {
                if (disposed.compareAndSet(false, true)) {
                    doCancelAction()
                }
            }
        })

        Disposer.register(disposable, object : Disposable {
            override fun dispose() {
                if (disposed.compareAndSet(false, true)) {
                    Disposer.dispose(myDisposable)
                }
            }
        })
    }

    override fun doCancelAction() {
        if (editorPanel.changes()) {
            if (OptionPane.showConfirmDialog(
                    this,
                    "文件尚未保存，你确定要退出吗？",
                    optionType = JOptionPane.OK_CANCEL_OPTION,
                ) != JOptionPane.OK_OPTION
            ) {
                return
            }
        }
        super.doCancelAction()
    }

    override fun createCenterPanel(): JComponent {
        return editorPanel
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }
}