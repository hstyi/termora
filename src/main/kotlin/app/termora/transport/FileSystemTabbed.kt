package app.termora.transport

import app.termora.*
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import org.apache.commons.lang3.StringUtils
import java.awt.Component
import java.awt.Point
import java.nio.file.FileSystems
import javax.swing.*
import kotlin.math.max


class FileSystemTabbed(
    private val transportManager: TransportManager,
    private val isLeft: Boolean = false
) : FlatTabbedPane(), Disposable {
    private val addBtn = JButton(Icons.add)

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        tabLayoutPolicy = SCROLL_TAB_LAYOUT
        isTabsClosable = true
        tabType = TabType.underlined
        styleMap = mapOf(
            "focusColor" to UIManager.getColor("TabbedPane.selectedBackground"),
        )


        val toolbar = JToolBar()
        toolbar.add(addBtn)
        trailingComponent = toolbar

        if (isLeft) {
            addTab(
                I18n.getString("termora.transport.local"), FileSystemPanel(
                    FileSystems.getDefault(),
                    host = Host(
                        id = "local",
                        name = I18n.getString("termora.transport.local"),
                        protocol = Protocol.Local,
                    )
                ).apply { reload() })
            setTabClosable(0, false)
        } else {
            addTab(
                I18n.getString("termora.transport.sftp.select-host"),
                SftpFileSystemPanel()
            )
        }

    }


    private fun initEvents() {
        addBtn.addActionListener {
            val dialog = HostTreeDialog(SwingUtilities.getWindowAncestor(this))
            dialog.location = Point(
                max(0, addBtn.locationOnScreen.x - dialog.width / 2 + addBtn.width / 2),
                addBtn.locationOnScreen.y + max(tabHeight, addBtn.height)
            )
            dialog.isVisible = true

            for (host in dialog.hosts) {
                val panel = SftpFileSystemPanel(host)
                addTab(host.name, panel)
                panel.connect()
            }

        }


        setTabCloseCallback { _, index ->
            removeTabAt(index)
        }
    }

    override fun removeTabAt(index: Int) {

        val fileSystemPanel = getFileSystemPanel(index)

        // 取消进行中的任务
        if (fileSystemPanel != null) {
            val transports = mutableListOf<Transport>()
            for (transport in transportManager.getTransports()) {
                if (transport.targetHolder == fileSystemPanel || transport.sourceHolder == fileSystemPanel) {
                    transports.add(transport)
                }
            }

            if (transports.isNotEmpty()) {
                if (OptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this),
                        I18n.getString("termora.transport.sftp.close-tab"),
                        messageType = JOptionPane.WARNING_MESSAGE,
                        optionType = JOptionPane.OK_CANCEL_OPTION
                    ) != JOptionPane.OK_OPTION
                ) {
                    return
                }
                transports.sortedBy { it.state == TransportState.Waiting }
                    .forEach { transportManager.removeTransport(it) }
            }
        }

        val c = getComponentAt(index)
        if (c is Disposable) {
            Disposer.dispose(c)
        }

        super.removeTabAt(index)

        if (tabCount == 0) {
            if (!isLeft) {
                addTab(
                    I18n.getString("termora.transport.sftp.select-host"),
                    SftpFileSystemPanel()
                )
            }
        }


    }

    override fun addTab(title: String, component: Component) {
        super.addTab(title, component)

        selectedIndex = tabCount - 1

        if (component is SftpFileSystemPanel) {
            component.addPropertyChangeListener("TabName") { e ->
                SwingUtilities.invokeLater {
                    val name = StringUtils.defaultIfEmpty(
                        e.newValue.toString(),
                        I18n.getString("termora.transport.sftp.select-host")
                    )
                    for (i in 0 until tabCount) {
                        if (getComponentAt(i) == component) {
                            setTitleAt(i, name)
                            break
                        }
                    }
                }
            }
        }

    }


    fun getSelectedFileSystemPanel(): FileSystemPanel? {
        return getFileSystemPanel(selectedIndex)
    }

    fun getFileSystemPanel(index: Int): FileSystemPanel? {
        if (index < 0) return null
        val c = getComponentAt(index)
        if (c is SftpFileSystemPanel) {
            val p = c.fileSystemPanel
            if (p != null) {
                return p
            }
        }

        if (c is FileSystemPanel) {
            return c
        }

        return null
    }

    override fun dispose() {
        while (tabCount > 0) {
            val c = getComponentAt(0)
            if (c is Disposable) {
                Disposer.dispose(c)
            }
            super.removeTabAt(0)
        }
    }

}