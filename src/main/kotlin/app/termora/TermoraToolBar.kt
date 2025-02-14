package app.termora

import app.termora.Application.ohMyJson
import app.termora.actions.ActionManager
import app.termora.actions.AnAction
import app.termora.actions.AnActionEvent
import app.termora.actions.SettingsAction
import app.termora.findeverywhere.FindEverywhereAction
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.util.SystemInfo
import com.jetbrains.WindowDecorations
import kotlinx.serialization.Serializable
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionContainerFactory
import java.awt.Insets
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Box
import javax.swing.JToolBar


@Serializable
data class ToolBarAction(
    val id: String,
    val visible: Boolean,
)

class TermoraToolBar(
    private val titleBar: WindowDecorations.CustomTitleBar,
    private val tabbedPane: FlatTabbedPane
) {
    private val properties by lazy { Database.getDatabase().properties }
    private val toolbar by lazy { MyToolBar().apply { rebuild(this) } }


    fun getJToolBar(): JToolBar {
        return toolbar
    }

    /**
     * 获取到所有的 Action
     */
    fun getAllActions(): List<ToolBarAction> {
        return listOf(
            ToolBarAction(Actions.SFTP, true),
            ToolBarAction(Actions.TERMINAL_LOGGER, true),
            ToolBarAction(Actions.MACRO, true),
            ToolBarAction(Actions.KEYWORD_HIGHLIGHT, true),
            ToolBarAction(Actions.KEY_MANAGER, true),
            ToolBarAction(Actions.MULTIPLE, true),
            ToolBarAction(FindEverywhereAction.FIND_EVERYWHERE, true),
            ToolBarAction(SettingsAction.SETTING, true),
        )
    }


    /**
     * 获取到所有 Action，会根据用户个性化排序/显示
     */
    fun getActions(): List<ToolBarAction> {
        val text = properties.getString(
            "Termora.ToolBar.Actions",
            StringUtils.EMPTY
        )

        val actions = getAllActions()

        if (text.isBlank()) {
            return actions
        }

        // 存储的 action
        val storageActions = (ohMyJson.runCatching {
            ohMyJson.decodeFromString<List<ToolBarAction>>(text)
        }.getOrNull() ?: return actions).toMutableList()

        for (action in actions) {
            // 如果存储的 action 不包含这个，那么这个可能是新增的，新增的默认显示出来
            if (storageActions.none { it.id == action.id }) {
                storageActions.addFirst(ToolBarAction(action.id, true))
            }
        }

        // 如果存储的 Action 在所有 Action 里没有，那么移除
        storageActions.removeIf { e -> actions.none { e.id == it.id } }

        return storageActions
    }

    fun rebuild() {
        rebuild(this.toolbar)
    }

    private fun rebuild(toolbar: JToolBar) {
        val actionManager = ActionManager.getInstance()
        val actionContainerFactory = ActionContainerFactory(actionManager)

        toolbar.removeAll()

        toolbar.add(actionContainerFactory.createButton(object : AnAction(StringUtils.EMPTY, Icons.add) {
            override fun actionPerformed(evt: AnActionEvent) {
                actionManager.getAction(FindEverywhereAction.FIND_EVERYWHERE)?.actionPerformed(evt)
            }

            override fun isEnabled(): Boolean {
                return actionManager.getAction(FindEverywhereAction.FIND_EVERYWHERE)?.isEnabled ?: false
            }
        }))

        toolbar.add(Box.createHorizontalGlue())

        if (SystemInfo.isLinux || SystemInfo.isWindows) {
            toolbar.add(Box.createHorizontalStrut(16))
        }


        // update btn
        val updateBtn = actionContainerFactory.createButton(actionManager.getAction(Actions.APP_UPDATE))
        updateBtn.isVisible = updateBtn.isEnabled
        updateBtn.addChangeListener { updateBtn.isVisible = updateBtn.isEnabled }
        toolbar.add(updateBtn)


        // 获取显示的Action，如果不是 false 那么就是显示出来
        for (action in getActions()) {
            if (action.visible) {
                actionManager.getAction(action.id)?.let {
                    toolbar.add(actionContainerFactory.createButton(it))
                }
            }
        }


        if (toolbar is MyToolBar) {
            toolbar.adjust()
        }

        toolbar.revalidate()
        toolbar.repaint()
    }

    private inner class MyToolBar : JToolBar() {
        init {
            // 监听窗口大小变动，然后修改边距避开控制按钮
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    adjust()
                }
            })
        }

        fun adjust() {
            if (SystemInfo.isMacOS) {
                val left = titleBar.leftInset.toInt()
                if (tabbedPane.tabAreaInsets.left != left) {
                    tabbedPane.tabAreaInsets = Insets(0, left, 0, 0)
                }
            } else if (SystemInfo.isWindows || SystemInfo.isLinux) {

                val right = titleBar.rightInset.toInt()
                val toolbar = this@MyToolBar
                for (i in 0 until toolbar.componentCount) {
                    val c = toolbar.getComponent(i)
                    if (c.name == "spacing") {
                        if (c.width == right) {
                            return
                        }
                        toolbar.remove(i)
                        break
                    }
                }

                if (right > 0) {
                    val spacing = Box.createHorizontalStrut(right)
                    spacing.name = "spacing"
                    toolbar.add(spacing)
                }
            }
        }
    }
}