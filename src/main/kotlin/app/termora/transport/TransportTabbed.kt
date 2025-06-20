package app.termora.transport

import app.termora.*
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import kotlinx.coroutines.CoroutineScope
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.FileSystems
import javax.swing.JButton
import javax.swing.JToolBar
import javax.swing.SwingUtilities

@Suppress("DuplicatedCode")
class TransportTabbed(
    private val coroutineScope: CoroutineScope,
    private val transferManager: InternalTransferManager,
) : FlatTabbedPane(), Disposable {
    private val addBtn = JButton(Icons.add)
    private val tabbed get() = this

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {
        super.setTabLayoutPolicy(SCROLL_TAB_LAYOUT)
        super.setTabsClosable(true)
        super.setTabType(TabType.underlined)
        super.setFocusable(false)


        val toolbar = JToolBar()
        toolbar.add(addBtn)
        super.setTrailingComponent(toolbar)

    }

    private fun initEvents() {
        addBtn.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                for (i in 0 until tabCount) {
                    val c = getComponentAt(i)
                    if (c !is TransportSelectionPanel) continue
                    if (c.state != TransportSelectionPanel.State.Initialized) continue
                    selectedIndex = i
                    SwingUtilities.invokeLater { c.requestFocusInWindow() }
                    return
                }

                // 添加一个新的
                addSelectionTab()
            }
        })

        // 右键菜单
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return
                }

                val index = indexAtLocation(e.x, e.y)
                if (index < 0) return

                showContextMenu(index, e)
            }
        })


        // 关闭 tab
        setTabCloseCallback { _, i -> tabCloseCallback(i) }
    }

    private fun tabCloseCallback(index: Int) {
        assertEventDispatchThread()

        val c = tabbed.getComponent(index)
        if (c == null) {
            tabbed.removeTabAt(index)
            return
        }

        // 删除并销毁
        tabbed.removeTabAt(index)

        if (tabbed.tabCount < 1) {
            addSelectionTab()
        }
    }

    fun addSelectionTab() {
        val c = TransportSelectionPanel(tabbed, coroutineScope, transferManager)
        addTab(I18n.getString("termora.transport.sftp.select-host"), c)
        selectedIndex = tabCount - 1
        SwingUtilities.invokeLater { c.requestFocusInWindow() }
    }

    fun addLocalTab() {
        val support = TransportSupport(FileSystems.getDefault(), "/Users/huangxingguang/Downloads/")
        addTab(
            I18n.getString("termora.transport.local"),
            TransportPanel(
                coroutineScope, transferManager,
                Host(name = "Local", protocol = "Local"),
                TransportSupportLoader { support })
        )
    }

    private fun showContextMenu(tabIndex: Int, e: MouseEvent) {
        val popupMenu = FlatPopupMenu()
        // 克隆
        val clone = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.clone"))
        clone.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {

            }
        })

        // 编辑
        val edit = popupMenu.add(I18n.getString("termora.keymgr.edit"))
        edit.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {

            }
        })

        edit.isEnabled = clone.isEnabled

        popupMenu.show(this, e.x, e.y)
    }

    fun getSelectedTransportPanel(): TransportPanel? {
        val index = selectedIndex
        if (index < 0) return null
        return getComponentAt(index) as? TransportPanel
    }

    override fun updateUI() {
        styleMap = mapOf(
            "focusColor" to DynamicColor("TabbedPane.background"),
            "hoverColor" to DynamicColor("TabbedPane.background"),
            "tabHeight" to 30,
            "showTabSeparators" to true,
            "tabSeparatorsFullHeight" to true,
        )
        super.updateUI()
    }

    override fun removeTabAt(index: Int) {
        val c = getComponentAt(index)
        if (c is Disposable) {
            Disposer.dispose(c)
        }
        super.removeTabAt(index)
    }

    override fun dispose() {
        while (tabCount > 0) removeTabAt(0)
    }
}