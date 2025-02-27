package app.termora.snippet

import app.termora.*
import java.awt.Dimension
import java.awt.Window
import javax.swing.JComponent
import javax.swing.UIManager
import kotlin.math.max

class SnippetDialog(owner: Window) : DialogWrapper(owner) {
    private val properties get() = Database.getDatabase().properties

    init {
        initViews()
        initEvents()
        init()
    }

    private fun initViews() {
        val w = properties.getString("SnippetDialog.width", "0").toIntOrNull() ?: 0
        val h = properties.getString("SnippetDialog.height", "0").toIntOrNull() ?: 0
        val x = properties.getString("SnippetDialog.x", "-1").toIntOrNull() ?: -1
        val y = properties.getString("SnippetDialog.y", "-1").toIntOrNull() ?: -1

        size = if (w > 0 && h > 0) {
            Dimension(w, h)
        } else {
            Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
        }
        isModal = true
        isResizable = true
        title = I18n.getString("termora.snippet.title")

        if (x != -1 && y != -1) {
            setLocation(max(x, 0), max(y, 0))
        } else {
            setLocationRelativeTo(owner)
        }
    }

    private fun initEvents() {
        Disposer.register(disposable, object : Disposable {
            override fun dispose() {
                properties.putString("SnippetDialog.width", width.toString())
                properties.putString("SnippetDialog.height", height.toString())
                properties.putString("SnippetDialog.x", x.toString())
                properties.putString("SnippetDialog.y", y.toString())
            }
        })
    }

    override fun createCenterPanel(): JComponent {
        return SnippetPanel().apply { Disposer.register(disposable, this) }
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }
}