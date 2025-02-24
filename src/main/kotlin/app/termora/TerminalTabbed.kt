package app.termora


import app.termora.actions.*
import app.termora.findeverywhere.BasicFilterFindEverywhereProvider
import app.termora.findeverywhere.FindEverywhereProvider
import app.termora.findeverywhere.FindEverywhereResult
import app.termora.terminal.DataKey
import app.termora.transport.TransportPanel
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.components.FlatPopupMenu
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import org.apache.commons.lang3.StringUtils
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.*
import javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT
import kotlin.math.min

class TerminalTabbed(
    private val windowScope: WindowScope,
    private val termoraToolBar: TermoraToolBar,
    private val tabbedPane: FlatTabbedPane,
) : JPanel(BorderLayout()), Disposable, TerminalTabbedManager, DataProvider {
    private val tabs = mutableListOf<TerminalTab>()
    private val customizeToolBarAWTEventListener = CustomizeToolBarAWTEventListener()
    private val toolbar = termoraToolBar.getJToolBar()
    private val actionManager = ActionManager.getInstance()
    private val dataProviderSupport = DataProviderSupport()
    private val titleProperty = UUID.randomUUID().toSimpleString()
    private val iconListener = PropertyChangeListener { e ->
        val source = e.source
        if (e.propertyName == "icon" && source is TerminalTab) {
            val index = tabs.indexOf(source)
            if (index >= 0) {
                tabbedPane.setIconAt(index, source.getIcon())
            }
        }
    }


    init {
        initView()
        initEvents()
    }

    private fun initView() {
        tabbedPane.tabLayoutPolicy = SCROLL_TAB_LAYOUT
        tabbedPane.isTabsClosable = true
        tabbedPane.tabType = FlatTabbedPane.TabType.card

        tabbedPane.trailingComponent = toolbar

        add(tabbedPane, BorderLayout.CENTER)

        windowScope.getOrCreate(TerminalTabbedManager::class) { this }

        dataProviderSupport.addData(DataProviders.TerminalTabbed, this)
        dataProviderSupport.addData(DataProviders.TerminalTabbedManager, this)
    }


    private fun initEvents() {
        // 关闭 tab
        tabbedPane.setTabCloseCallback { _, i -> removeTabAt(i, true) }

        // 选中变动
        tabbedPane.addPropertyChangeListener("selectedIndex") { evt ->
            val oldIndex = evt.oldValue as Int
            val newIndex = evt.newValue as Int

            if (oldIndex >= 0 && tabs.size > newIndex) {
                tabs[oldIndex].onLostFocus()
            }

            tabbedPane.getComponentAt(newIndex).requestFocusInWindow()

            if (newIndex >= 0 && tabs.size > newIndex) {
                tabs[newIndex].onGrabFocus()
            }

        }


        // 右键菜单
        tabbedPane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return
                }

                val index = tabbedPane.indexAtLocation(e.x, e.y)
                if (index < 0) return

                showContextMenu(index, e)
            }
        })


        // 点击
        tabbedPane.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    val index = tabbedPane.indexAtLocation(e.x, e.y)
                    if (index > 0) {
                        tabbedPane.getComponentAt(index).requestFocusInWindow()
                    }
                }
            }
        })

        // 注册全局搜索
        FindEverywhereProvider.getFindEverywhereProviders(windowScope)
            .add(BasicFilterFindEverywhereProvider(object : FindEverywhereProvider {
                override fun find(pattern: String): List<FindEverywhereResult> {
                    val results = mutableListOf<FindEverywhereResult>()
                    for (i in 0 until tabbedPane.tabCount) {
                        val c = tabbedPane.getComponentAt(i)
                        if (c is WelcomePanel || c is TransportPanel) {
                            continue
                        }
                        results.add(
                            SwitchFindEverywhereResult(
                                tabbedPane.getTitleAt(i),
                                tabbedPane.getIconAt(i),
                                tabbedPane.getComponentAt(i)
                            )
                        )
                    }
                    return results
                }

                override fun group(): String {
                    return I18n.getString("termora.find-everywhere.groups.opened-hosts")
                }

                override fun order(): Int {
                    return Integer.MIN_VALUE + 1
                }
            }))


        // 监听全局事件
        toolkit.addAWTEventListener(customizeToolBarAWTEventListener, AWTEvent.MOUSE_EVENT_MASK)

    }

    private fun removeTabAt(index: Int, disposable: Boolean = true) {
        if (tabbedPane.isTabClosable(index)) {
            val tab = tabs[index]

            if (disposable) {
                if (!tab.canClose()) {
                    return
                }
            }

            tab.onLostFocus()
            tab.removePropertyChangeListener(iconListener)

            // remove tab
            tabbedPane.removeTabAt(index)

            // remove ele
            tabs.removeAt(index)

            // 新的获取到焦点
            tabs[tabbedPane.selectedIndex].onGrabFocus()

            // 新的真正获取焦点
            tabbedPane.getComponentAt(tabbedPane.selectedIndex).requestFocusInWindow()

            if (disposable) {
                Disposer.dispose(tab)
            }
        }
    }

    private fun showContextMenu(tabIndex: Int, e: MouseEvent) {
        val c = tabbedPane.getComponentAt(tabIndex) as JComponent
        val tab = tabs[tabIndex]

        val popupMenu = FlatPopupMenu()

        // 修改名称
        val rename = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.rename"))
        rename.addActionListener {
            if (tabIndex > 0) {
                val dialog = InputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    title = rename.text,
                    text = tabbedPane.getTitleAt(tabIndex),
                )
                val text = dialog.getText()
                if (!text.isNullOrBlank()) {
                    tabbedPane.setTitleAt(tabIndex, text)
                    c.putClientProperty(titleProperty, text)
                }
            }

        }

        // 克隆
        val clone = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.clone"))
        clone.addActionListener { evt ->
            if (tab is HostTerminalTab) {
                actionManager
                    .getAction(OpenHostAction.OPEN_HOST)
                    .actionPerformed(OpenHostActionEvent(this, tab.host, evt))
            }
        }

        // 在新窗口中打开
        val openInNewWindow = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.open-in-new-window"))
        openInNewWindow.addActionListener(object : AnAction() {
            override fun actionPerformed(evt: AnActionEvent) {
                val owner = evt.getData(DataProviders.TermoraFrame) ?: return
                if (tabIndex > 0) {
                    val title = tabbedPane.getTitleAt(tabIndex)
                    removeTabAt(tabIndex, false)
                    val dialog = TerminalTabDialog(
                        owner = owner,
                        terminalTab = tab,
                        size = Dimension(min(size.width, 1280), min(size.height, 800))
                    )
                    dialog.title = title
                    Disposer.register(dialog, tab)
                    Disposer.register(this@TerminalTabbed, dialog)
                    dialog.isVisible = true
                }
            }
        })

        if (tab is HostTerminalTab) {
            val openHostAction = actionManager.getAction(OpenHostAction.OPEN_HOST)
            if (openHostAction != null) {
                if (tab.host.protocol == Protocol.SSH || tab.host.protocol == Protocol.SFTPPty) {
                    popupMenu.addSeparator()
                    val sftpCommand = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.sftp-command"))
                    sftpCommand.addActionListener { openSFTPPtyTab(tab, openHostAction, it) }
                }
            }
        }

        popupMenu.addSeparator()

        // 关闭
        val close = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.close"))
        close.addActionListener {
            tabbedPane.tabCloseCallback?.accept(tabbedPane, tabIndex)
        }

        // 关闭其他标签页
        popupMenu.add(I18n.getString("termora.tabbed.contextmenu.close-other-tabs")).addActionListener {
            for (i in tabbedPane.tabCount - 1 downTo tabIndex + 1) {
                tabbedPane.tabCloseCallback?.accept(tabbedPane, i)
            }
            for (i in 1 until tabIndex) {
                tabbedPane.tabCloseCallback?.accept(tabbedPane, tabIndex - i)
            }
        }

        // 关闭所有标签页
        popupMenu.add(I18n.getString("termora.tabbed.contextmenu.close-all-tabs")).addActionListener {
            for (i in 0 until tabbedPane.tabCount) {
                tabbedPane.tabCloseCallback?.accept(tabbedPane, tabbedPane.tabCount - 1)
            }
        }


        close.isEnabled = tab.canClose()
        rename.isEnabled = close.isEnabled
        clone.isEnabled = close.isEnabled
        openInNewWindow.isEnabled = close.isEnabled

        // 如果不允许克隆
        if (clone.isEnabled && !tab.canClone()) {
            clone.isEnabled = false
        }

        if (close.isEnabled) {
            popupMenu.addSeparator()
            val reconnect = popupMenu.add(I18n.getString("termora.tabbed.contextmenu.reconnect"))
            reconnect.addActionListener {
                if (tabIndex > 0) {
                    tabs[tabIndex].reconnect()
                }
            }

            reconnect.isEnabled = tabs[tabIndex].canReconnect()
        }

        popupMenu.show(this, e.x, e.y)
    }


    private fun addTab(index: Int, tab: TerminalTab, selected: Boolean) {
        val c = tab.getJComponent()
        val title = (c.getClientProperty(titleProperty) ?: tab.getTitle()).toString()

        tabbedPane.insertTab(
            title,
            tab.getIcon(),
            c,
            StringUtils.EMPTY,
            index
        )

        // 设置标题
        c.putClientProperty(titleProperty, title)
        // 监听 icons 变化
        tab.addPropertyChangeListener(iconListener)

        tabs.add(index, tab)

        if (selected) {
            tabbedPane.selectedIndex = index
        }

        tabbedPane.setTabClosable(index, tab.canClose())

        Disposer.register(this, tab)
    }

    private fun openSFTPPtyTab(tab: HostTerminalTab, openHostAction: Action, evt: EventObject) {
        if (!SFTPPtyTerminalTab.canSupports) {
            OptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                I18n.getString("termora.tabbed.contextmenu.sftp-not-install"),
                messageType = JOptionPane.ERROR_MESSAGE
            )
            return
        }

        var host = tab.host

        if (host.protocol == Protocol.SSH) {
            val envs = tab.host.options.envs().toMutableMap()
            val currentDir = tab.getData(DataProviders.Terminal)?.getTerminalModel()
                ?.getData(DataKey.CurrentDir, StringUtils.EMPTY) ?: StringUtils.EMPTY

            if (currentDir.isNotBlank()) {
                envs["CurrentDir"] = currentDir
            }

            host = host.copy(
                protocol = Protocol.SFTPPty, updateDate = System.currentTimeMillis(),
                options = host.options.copy(env = envs.toPropertiesString())
            )
        }

        openHostAction.actionPerformed(OpenHostActionEvent(this, host, evt))
    }

    /**
     * 对着 ToolBar 右键
     */
    private inner class CustomizeToolBarAWTEventListener : AWTEventListener, Disposable {
        init {
            Disposer.register(this@TerminalTabbed, this)
        }

        override fun eventDispatched(event: AWTEvent) {
            if (event !is MouseEvent || event.id != MouseEvent.MOUSE_CLICKED || !SwingUtilities.isRightMouseButton(event)) return
            // 如果 ToolBar 没有显示
            if (!toolbar.isShowing) return
            // 如果不是作用于在 ToolBar 上面
            if (!Rectangle(toolbar.locationOnScreen, toolbar.size).contains(event.locationOnScreen)) return

            // 显示右键菜单
            showContextMenu(event)
        }

        private fun showContextMenu(event: MouseEvent) {
            val popupMenu = FlatPopupMenu()
            popupMenu.add(I18n.getString("termora.toolbar.customize-toolbar")).addActionListener {
                val dialog = CustomizeToolBarDialog(
                    SwingUtilities.getWindowAncestor(this@TerminalTabbed),
                    termoraToolBar
                )
                if (dialog.open()) {
                    termoraToolBar.rebuild()
                }
            }
            popupMenu.show(event.component, event.x, event.y)
        }

        override fun dispose() {
            toolkit.removeAWTEventListener(this)
        }
    }

    /*private inner class CustomizeToolBarDialog(owner: Window) : DialogWrapper(owner) {
        init {
            size = Dimension(UIManager.getInt("Dialog.width"), UIManager.getInt("Dialog.height"))
            isModal = true
            title = I18n.getString("termora.setting")
            setLocationRelativeTo(null)

            init()
        }

        override fun createCenterPanel(): JComponent {
            val model = DefaultListModel<String>()
            val checkBoxList = CheckBoxList(model)
            checkBoxList.fixedCellHeight = UIManager.getInt("Tree.rowHeight")
            model.addElement("Test")
            return checkBoxList
        }

    }*/

    private inner class SwitchFindEverywhereResult(
        private val title: String,
        private val icon: Icon?,
        private val c: Component
    ) : FindEverywhereResult {

        override fun actionPerformed(e: ActionEvent) {
            tabbedPane.selectedComponent = c
        }

        override fun getIcon(isSelected: Boolean): Icon {
            if (isSelected) {
                if (!FlatLaf.isLafDark()) {
                    if (icon is DynamicIcon) {
                        return icon.dark
                    }
                }
            }
            return icon ?: super.getIcon(isSelected)
        }

        override fun toString(): String {
            return title
        }
    }


    override fun dispose() {
    }

    override fun addTerminalTab(tab: TerminalTab, selected: Boolean) {
        addTab(tabs.size, tab, selected)
    }

    override fun addTerminalTab(index: Int, tab: TerminalTab, selected: Boolean) {
        addTab(index, tab, selected)
    }

    override fun getSelectedTerminalTab(): TerminalTab? {
        val index = tabbedPane.selectedIndex
        if (index == -1) {
            return null
        }

        return tabs[index]
    }

    override fun getTerminalTabs(): List<TerminalTab> {
        return tabs
    }

    override fun setSelectedTerminalTab(tab: TerminalTab) {
        for (index in tabs.indices) {
            if (tabs[index] == tab) {
                tabbedPane.selectedIndex = index
                break
            }
        }
    }

    override fun closeTerminalTab(tab: TerminalTab, disposable: Boolean) {
        for (i in 0 until tabs.size) {
            if (tabs[i] == tab) {
                removeTabAt(i, disposable)
                break
            }
        }
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        if (dataKey == DataProviders.TerminalTab) {
            dataProviderSupport.removeData(dataKey)
            if (tabbedPane.selectedIndex >= 0 && tabs.size > tabbedPane.selectedIndex) {
                dataProviderSupport.addData(dataKey, tabs[tabbedPane.selectedIndex])
            }
        }
        return dataProviderSupport.getData(dataKey)
    }


}