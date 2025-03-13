package app.termora.sftp

import app.termora.*
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import java.awt.Point
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JToolBar
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.math.max

@Suppress("DuplicatedCode")
class SFTPTabbed(private val transportManager: TransportManager) : FlatTabbedPane(), Disposable {
    private val addBtn = JButton(Icons.add)
    private val tabbed = this
    private val disposed = AtomicBoolean(false)

    val isDisposed get() = disposed.get()

    init {
        initViews()
        initEvents()
    }

    private fun initViews() {
        super.setTabLayoutPolicy(SCROLL_TAB_LAYOUT)
        super.setTabsClosable(true)
        super.setTabType(TabType.underlined)
        super.setStyleMap(
            mapOf(
                "focusColor" to UIManager.getColor("TabbedPane.selectedBackground"),
                "hoverColor" to UIManager.getColor("TabbedPane.background"),
                "tabHeight" to 30
            )
        )


        val toolbar = JToolBar()
        toolbar.add(addBtn)
        super.setTrailingComponent(toolbar)

    }

    private fun initEvents() {
        addBtn.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val dialog = NewHostTreeDialog(SwingUtilities.getWindowAncestor(tabbed))
                dialog.location = Point(
                    max(0, addBtn.locationOnScreen.x - dialog.width / 2 + addBtn.width / 2),
                    addBtn.locationOnScreen.y + max(tabHeight, addBtn.height)
                )
                dialog.setFilter { it.host.protocol == Protocol.SSH }
                dialog.setTreeName("SFTPTabbed.Tree")
                dialog.allowMulti = true
                dialog.isVisible = true

                val hosts = dialog.hosts
                if (hosts.isEmpty()) return

                for (host in hosts) {
                    addSFTPFileSystemViewPanelTab(host)
                }

            }
        })

    }

    fun addSFTPFileSystemViewPanelTab(host: Host) {
        val panel = SFTPFileSystemViewPanel(host, transportManager)
        addTab(host.name, panel)
        panel.connect()
        selectedIndex = tabCount - 1
    }

    /**
     * 获取当前的 FileSystemViewPanel
     */
    fun getSelectedFileSystemViewPanel(): FileSystemViewPanel? {
        return getFileSystemViewPanel(selectedIndex)
    }


    fun getFileSystemViewPanel(index: Int): FileSystemViewPanel? {
        if (tabCount < 1 || index < 0) return null

        val c = getComponentAt(index)
        if (c is FileSystemViewPanel) {
            return c
        }

        if (c is SFTPFileSystemViewPanel) {
            return c.getData(SFTPDataProviders.FileSystemViewPanel)
        }

        return null
    }


    override fun removeTabAt(index: Int) {
        val c = getComponentAt(index)
        if (c is Disposable) {
            Disposer.dispose(c)
        }
        super.removeTabAt(index)
    }

    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            while (tabCount > 0) removeTabAt(0)
        }
    }
}