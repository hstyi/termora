package app.termora.transfer

import app.termora.*
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.plugin.internal.local.LocalProtocolProvider
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import org.apache.commons.lang3.SystemUtils
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.FileSystems
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.JToolBar
import javax.swing.SwingUtilities

@Suppress("DuplicatedCode")
class TransportTabbed(
    private val transferManager: TransferManager,
    private val internalTransferManager: InternalTransferManager
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

    fun tabCloseCallback(index: Int) {
        assertEventDispatchThread()

        if (isTabClosable(index).not()) return

        val c = tabbed.getComponentAt(index)
        if (c == null) {
            tabbed.removeTabAt(index)
            return
        }

        if (c is TransportPanel) {
            if (tabClose(c).not()) return
        }

        // 删除并销毁
        tabbed.removeTabAt(index)

        if (tabbed.tabCount < 1) {
            addSelectionTab()
        }
    }

    private fun tabClose(c: TransportPanel): Boolean {
        if (transferManager.getTransferCount() < 1) return true
        if (c.loader.isLoaded.not()) return false
        val fileSystem = c.getFileSystem()
        val transfers = transferManager.getTransfers()
            .filter { it.source().fileSystem == fileSystem || it.target().fileSystem == fileSystem }
        if (transfers.isEmpty()) return true

        if (OptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.transport.sftp.close-tab"),
                messageType = JOptionPane.QUESTION_MESSAGE,
                optionType = JOptionPane.OK_CANCEL_OPTION
            ) != JOptionPane.OK_OPTION
        ) return false

        // 删除所有关联任务
        for (transfer in transfers) {
            transferManager.removeTransfer(transfer.id())
        }

        return true
    }

    fun addSelectionTab() {
        val c = TransportSelectionPanel(tabbed, internalTransferManager)
        addTab(I18n.getString("termora.transport.sftp.select-host"), c)
        selectedIndex = tabCount - 1
        SwingUtilities.invokeLater { c.requestFocusInWindow() }
    }

    fun addLocalTab() {
        val host = Host(name = "Local", protocol = LocalProtocolProvider.PROTOCOL)
        val support = TransportSupport(FileSystems.getDefault(), SystemUtils.USER_HOME)
        val panel = TransportPanel(internalTransferManager, host, TransportSupportLoader { support })
        addTab(I18n.getString("termora.transport.local"), panel)
        super.setTabClosable(0, false)
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
        return getTransportPanel(index)
    }

    fun getTransportPanel(index: Int): TransportPanel? {
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