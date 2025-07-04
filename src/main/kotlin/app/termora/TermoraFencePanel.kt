package app.termora

import app.termora.tree.NewHostTree
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.util.SystemInfo
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import javax.swing.*


class TermoraFencePanel(
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

        add(mySplitPane, BorderLayout.CENTER)
    }

    private fun initEvents() {
        Disposer.register(this, leftTreePanel)
        splitPane.addPropertyChangeListener("dividerLocation") { mySplitPane.doLayout() }
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
            box.add(Box.createHorizontalGlue())
            if (SystemInfo.isMacOS.not()) box.add(label)
            box.add(Box.createHorizontalGlue())

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

    override fun dispose() {
        enableManager.setFlag("Termora.Fence.dividerLocation", splitPane.dividerLocation)
    }

    fun getHostTree(): NewHostTree {
        return leftTreePanel.hostTree
    }

}