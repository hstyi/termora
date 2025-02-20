package app.termora.terminal.panel.vw

import app.termora.*
import com.formdev.flatlaf.extras.components.FlatToolBar
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.*
import kotlin.math.max
import kotlin.math.min


open class VisualWindowPanel(protected val id: String, protected val visualWindowManager: VisualWindowManager) :
    JPanel(BorderLayout()), VisualWindow {

    protected val properties get() = Database.getDatabase().properties
    private val titleLabel = JLabel()
    private val toolbar = FlatToolBar()
    private val visualWindow = this
    private val resizer = VisualWindowResizer(this) { !isWindow }
    private var isWindow = false
        set(value) {
            val oldValue = field
            field = value
            firePropertyChange("isWindow", oldValue, value)
        }
    private var dialog: VisualWindowDialog? = null
    private var oldBounds = Rectangle()
    private var toggleWindowBtn = JButton(Icons.openInNewWindow)
    private val closeWindowListener = object : WindowAdapter() {
        override fun windowClosed(e: WindowEvent) {
            close()
        }
    }


    var title: String
        set(value) {
            titleLabel.text = value
        }
        get() = titleLabel.text


    protected fun initVisualWindowPanel() {
        initViews()
        initEvents()
        initToolBar()
    }

    private fun initViews() {
        border = BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)

        val x = properties.getString("VisualWindow.${id}.location.x", "-1").toIntOrNull() ?: -1
        val y = properties.getString("VisualWindow.${id}.location.y", "-1").toIntOrNull() ?: -1
        val w = properties.getString("VisualWindow.${id}.location.width", "-1").toIntOrNull() ?: -1
        val h = properties.getString("VisualWindow.${id}.location.height", "-1").toIntOrNull() ?: -1

        if (x >= 0 && y >= 0) {
            setLocation(x, y)
        } else {
            setLocation(200, 200)
        }

        if (w > 0 && h > 0) setSize(w, h) else setSize(400, 200)

    }

    private fun initEvents() {
        val dragListener = DragListener()
        toolbar.addMouseListener(dragListener)
        toolbar.addMouseMotionListener(dragListener)

        // 监听全局事件
        Toolkit.getDefaultToolkit().addAWTEventListener(object : AWTEventListener {
            override fun eventDispatched(event: AWTEvent) {
                if (event is MouseEvent) {
                    if (event.id == MouseEvent.MOUSE_PRESSED) {
                        val c = event.component ?: return
                        if (SwingUtilities.isDescendingFrom(c, visualWindow)) {
                            visualWindowManager.moveToFront(visualWindow)
                        }
                    }
                }
            }

        }, MouseEvent.MOUSE_EVENT_MASK)

        // 阻止事件穿透
        addMouseListener(object : MouseAdapter() {})

        toggleWindowBtn.addActionListener { toggleWindow() }

        addPropertyChangeListener("isWindow", object : PropertyChangeListener {
            override fun propertyChange(evt: PropertyChangeEvent) {
                if (isWindow) {
                    border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)
                    toggleWindowBtn.icon = Icons.openInToolWindow
                } else {
                    border = BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)
                    toggleWindowBtn.icon = Icons.openInNewWindow
                }
            }
        })
    }

    private fun initToolBar() {
        toolbar.add(JLabel(Icons.empty))
        toolbar.add(JLabel(Icons.empty))
        toolbar.add(Box.createHorizontalGlue())
        toolbar.add(titleLabel)
        toolbar.add(Box.createHorizontalGlue())
        toolbar.add(toggleWindowBtn)
        toolbar.add(JButton(Icons.close).apply { addActionListener { Disposer.dispose(visualWindow) } })
        toolbar.border = BorderFactory.createMatteBorder(0, 0, 1, 0, DynamicColor.BorderColor)
        add(toolbar, BorderLayout.NORTH)
    }

    override fun dispose() {

        val bounds = if (isWindow) oldBounds else bounds
        properties.putString("VisualWindow.${id}.location.x", bounds.x.toString())
        properties.putString("VisualWindow.${id}.location.y", bounds.y.toString())
        properties.putString("VisualWindow.${id}.location.width", bounds.width.toString())
        properties.putString("VisualWindow.${id}.location.height", bounds.height.toString())

        resizer.uninstall()

        this.close()

    }

    final override fun getJComponent(): JComponent {
        return this
    }

    override fun isWindow(): Boolean {
        return isWindow
    }

    override fun getWindow(): Window? {
        return dialog
    }

    protected open fun getWindowTitle(): String {
        return id
    }

    override fun toggleWindow() {

        if (isWindow) {
            // 提前移除 dialog 的关闭事件
            dialog?.removeWindowListener(closeWindowListener)
        }

        isWindow = !isWindow
        dialog?.dispose()
        dialog = null

        if (isWindow) {
            oldBounds = bounds
            // 变基
            visualWindowManager.rebaseVisualWindow(this)

            val dialog = VisualWindowDialog().apply { dialog = this }
            dialog.addWindowListener(closeWindowListener)
            dialog.isVisible = true

        } else {
            bounds = oldBounds
            visualWindowManager.removeVisualWindow(visualWindow)
            visualWindowManager.addVisualWindow(visualWindow)
        }
    }

    private inner class DragListener() : MouseAdapter() {
        private var startPoint: Point? = null

        override fun mousePressed(e: MouseEvent) {
            if (isWindow) {
                startPoint = null
                return
            }
            startPoint = SwingUtilities.convertPoint(visualWindow, e.getPoint(), visualWindow.getParent())
        }

        override fun mouseDragged(e: MouseEvent) {
            val startPoint = this.startPoint ?: return
            val newPoint = SwingUtilities.convertPoint(visualWindow, e.getPoint(), visualWindow.getParent())
            val dimension = visualWindowManager.getDimension()

            val x = min(
                visualWindow.getX() + (newPoint.x - startPoint.x),
                dimension.width - visualWindow.width
            )

            val y = min(
                visualWindow.getY() + (newPoint.y - startPoint.y),
                dimension.height - visualWindow.height
            )

            visualWindow.setBounds(max(x, 0), max(y, 0), visualWindow.getWidth(), visualWindow.getHeight())

            this.startPoint = newPoint
        }

        override fun mouseReleased(e: MouseEvent) {
            visualWindowManager.moveToFront(visualWindow)
        }

    }


    protected open fun close() {
        SwingUtilities.invokeLater {
            if (isWindow()) {
                dialog?.dispose()
                dialog = null
            }
            visualWindowManager.removeVisualWindow(visualWindow)
        }
    }

    private inner class VisualWindowDialog : DialogWrapper(null) {

        init {
            isModal = false
            controlsVisible = false
            isResizable = true
            title = getWindowTitle()

            initEvents()

            init()


            val x = properties.getString("VisualWindow.${id}.dialog.location.x", "-1").toIntOrNull() ?: -1
            val y = properties.getString("VisualWindow.${id}.dialog.location.y", "-1").toIntOrNull() ?: -1
            val w = properties.getString("VisualWindow.${id}.dialog.location.width", "-1").toIntOrNull() ?: -1
            val h = properties.getString("VisualWindow.${id}.dialog.location.height", "-1").toIntOrNull() ?: -1

            if (w > 0 && h > 0) setSize(w, h) else pack()

            if (x >= 0 && y >= 0) {
                setLocation(x, y)
            } else {
                setLocationRelativeTo(null)
            }


        }

        private fun initEvents() {
            Disposer.register(disposable, object : Disposable {
                override fun dispose() {
                    properties.putString("VisualWindow.${id}.dialog.location.x", x.toString())
                    properties.putString("VisualWindow.${id}.dialog.location.y", y.toString())
                    properties.putString("VisualWindow.${id}.dialog.location.width", width.toString())
                    properties.putString("VisualWindow.${id}.dialog.location.height", height.toString())
                }
            })
        }


        override fun createCenterPanel(): JComponent {
            return getJComponent()
        }

        override fun createSouthPanel(): JComponent? {
            return null
        }
    }
}