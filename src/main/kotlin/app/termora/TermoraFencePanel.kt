package app.termora

import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.plugin.internal.extension.DynamicExtensionHandler
import app.termora.tree.NewHostTree
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.extras.components.FlatToolBar
import com.formdev.flatlaf.util.SystemInfo
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.*
import javax.swing.*
import javax.swing.tree.TreePath
import kotlin.math.max


class TermoraFencePanel(
    private val ws: WindowScope,
    private val terminalTabbed: TerminalTabbed,
    private val tabbed: FlatTabbedPane,
    private val moveMouseAdapter: MouseAdapter,
) : JPanel(BorderLayout()), Disposable {
    private val splitPane = object : JSplitPane() {
        override fun updateUI() {
            setUI(SplitPaneUI())
            revalidate()
        }
    }
    private val leftTreePanel = LeftTreePanel()
    private val mySplitPane = JSplitPaneWithZeroSizeDivider(splitPane) { tabbed.tabHeight }
    private val enableManager get() = EnableManager.getInstance()
    private val toolbar = FlatToolBar().apply { isFloatable = false }
    private var dividerLocation = 0

    init {
        initView()
        initEvents()
    }

    private fun initView() {

        splitPane.border = null

        splitPane.leftComponent = leftTreePanel
        splitPane.rightComponent = terminalTabbed
        splitPane.dividerSize = 0
        splitPane.dividerLocation = enableManager.getFlag("Termora.Fence.dividerLocation", 220)

        leftTreePanel.preferredSize = Dimension(180, -1)

        tabbed.tabType = FlatTabbedPane.TabType.underlined
        tabbed.tabAreaInsets = null

        // macOS 避开控制栏
        if (SystemInfo.isMacOS) {
            toolbar.add(Box.createHorizontalStrut(76))
        }

        toolbar.add(createColspanAction())
        tabbed.leadingComponent = toolbar
        toolbar.isVisible = false

        add(mySplitPane, BorderLayout.CENTER)
    }

    private fun initEvents() {
        Disposer.register(this, leftTreePanel)
        splitPane.addPropertyChangeListener("dividerLocation") { mySplitPane.doLayout() }

        leftTreePanel.addComponentListener(object : ComponentAdapter() {
            override fun componentHidden(e: ComponentEvent) {
                toolbar.isVisible = true
                enableManager.setFlag("Termora.Fence.colspan", true)
            }

            override fun componentShown(e: ComponentEvent) {
                toolbar.isVisible = false
                enableManager.setFlag("Termora.Fence.colspan", false)
            }
        })

        actionMap.put("toggle", createColspanAction())
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_B,
                toolkit.menuShortcutKeyMaskEx or KeyEvent.SHIFT_DOWN_MASK
            ), "toggle"
        )

        splitPane.addPropertyChangeListener("dividerLocation") {
            if (leftTreePanel.isVisible)
                enableManager.setFlag("Termora.Fence.dividerLocation", max(splitPane.dividerLocation, 10))
        }

        if (enableManager.getFlag("Termora.Fence.colspan", false)) {
            toggle()
        }


        DynamicExtensionHandler.getInstance()
            .register(TerminalTabbedContextMenuExtension::class.java, object : TerminalTabbedContextMenuExtension {
                override fun createJMenuItem(
                    windowScope: WindowScope,
                    tab: TerminalTab
                ): JMenuItem {
                    if (windowScope != ws) throw UnsupportedOperationException()
                    if (tab !is HostTerminalTab) throw UnsupportedOperationException()
                    if (tab.host.isTemporary) throw UnsupportedOperationException()
                    if (tab.host.id == "local") throw UnsupportedOperationException()

                    val item = JMenuItem(I18n.getString("termora.tabbed.contextmenu.select-host"))
                    item.addActionListener(object : AbstractAction() {
                        override fun actionPerformed(e: ActionEvent) {
                            val tree = getHostTree()
                            for (node in tree.simpleTreeModel.root.getAllChildren()) {
                                if (node.id == tab.host.id) {
                                    tree.selectionPath = TreePath(tree.simpleTreeModel.getPathToRoot(node))
                                    tree.requestFocusInWindow()
                                    break
                                }
                            }
                        }
                    })

                    return item
                }

                override fun ordered(): Long {
                    return Long.MAX_VALUE
                }

            }).let { Disposer.register(this, it) }
    }

    private inner class LeftTreePanel : JPanel(BorderLayout()), Disposable {
        val hostTree = NewHostTree()
        private val box = JToolBar().apply { isFloatable = false }

        init {
            initView()
            initEvents()
        }

        private fun initView() {
            val scrollPane = JScrollPane(hostTree)
            hostTree.name = "FenceHostTree"
            hostTree.restoreExpansions()
            box.preferredSize = Dimension(-1, tabbed.tabHeight)

            val label = JLabel(Application.getName())
            label.foreground = UIManager.getColor("textInactiveText")
            label.font = label.font.deriveFont(Font.BOLD)
            // 与最后一个按钮对冲，使其宽度和谐
            box.add(Box.createHorizontalStrut(24))
            box.add(Box.createHorizontalGlue())
            if (SystemInfo.isMacOS.not()) {
                box.add(label)
            }
            box.add(Box.createHorizontalGlue())
            box.add(createColspanAction())

            if (SystemInfo.isMacOS || SystemInfo.isLinux) {
                box.addMouseListener(moveMouseAdapter)
                box.addMouseMotionListener(moveMouseAdapter)
            }

            scrollPane.verticalScrollBar.unitIncrement = 16
            scrollPane.horizontalScrollBar.unitIncrement = 16
            scrollPane.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor),
                BorderFactory.createEmptyBorder(4, 4, 0, 4)
            )

            add(box, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }

        private fun initEvents() {
            Disposer.register(this, hostTree)
        }
    }

    private fun createColspanAction(): Action {
        return object : AnAction(Icons.dataColumn) {
            init {
                val text = I18n.getString("termora.welcome.toggle-sidebar")
                putValue(SHORT_DESCRIPTION, "$text (${if (SystemInfo.isMacOS) '⌘' else "Ctrl"} + Shift + B)")
            }

            override fun actionPerformed(evt: AnActionEvent) {
                toggle()
            }
        }
    }

    private fun toggle() {
        if (leftTreePanel.isVisible) dividerLocation = splitPane.dividerLocation
        leftTreePanel.isVisible = leftTreePanel.isVisible.not()
        if (leftTreePanel.isVisible) splitPane.dividerLocation = dividerLocation
        mySplitPane.doLayout()
    }


    fun getHostTree(): NewHostTree {
        return leftTreePanel.hostTree
    }

}