package app.termora

import app.termora.Application.ohMyJson
import app.termora.db.Database
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.util.SystemInfo
import com.jetbrains.WindowDecorations
import kotlinx.serialization.Serializable
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionContainerFactory
import org.jdesktop.swingx.action.ActionManager
import java.awt.Insets
import java.awt.event.ActionEvent
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
    private val properties by lazy { Database.instance.properties }
    private val toolbar by lazy { MyToolBar().apply { rebuild(this) } }


    fun getJToolBar(): JToolBar {
        return toolbar
    }


    fun getShownActions(): List<ToolBarAction> {
        val text = properties.getString(
            "Termora.ToolBar.Actions",
            StringUtils.EMPTY
        )

        if (text.isBlank()) {
            return getAllActions().map { ToolBarAction(it, true) }
        }

        return ohMyJson.runCatching {
            ohMyJson.decodeFromString<List<ToolBarAction>>(text)
        }.getOrNull() ?: getAllActions().map { ToolBarAction(it, true) }
    }

    fun getAllActions(): List<String> {
        return listOf(
            Actions.SFTP,
            Actions.TERMINAL_LOGGER,
            Actions.MACRO,
            Actions.KEYWORD_HIGHLIGHT,
            Actions.KEY_MANAGER,
            Actions.MULTIPLE,
            Actions.FIND_EVERYWHERE,
            Actions.SETTING,
        )
    }

    fun rebuild() {
        rebuild(this.toolbar)
    }

    private fun rebuild(toolbar: JToolBar) {
        val actionManager = ActionManager.getInstance()
        val actionContainerFactory = ActionContainerFactory(actionManager)

        toolbar.removeAll()

        toolbar.add(actionContainerFactory.createButton(object : AnAction(StringUtils.EMPTY, Icons.add) {
            override fun actionPerformed(e: ActionEvent?) {
                actionManager.getAction(Actions.FIND_EVERYWHERE)?.actionPerformed(e)
            }

            override fun isEnabled(): Boolean {
                return actionManager.getAction(Actions.FIND_EVERYWHERE)?.isEnabled ?: false
            }
        }))

        toolbar.add(Box.createHorizontalGlue())


        // update btn
        val updateBtn = actionContainerFactory.createButton(actionManager.getAction(Actions.APP_UPDATE))
        updateBtn.isVisible = updateBtn.isEnabled
        updateBtn.addChangeListener { updateBtn.isVisible = updateBtn.isEnabled }
        toolbar.add(updateBtn)

        // 获取显示的Action，如果不是 false 那么就是显示出来
        val actions = getShownActions().associate { Pair(it.id, it.visible) }
        for (action in getAllActions()) {
            // actions[action] 有可能是 null，那么极有可能表示这个 Action 是新增的
            if (actions[action] != false) {
                actionManager.getAction(action)?.let {
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