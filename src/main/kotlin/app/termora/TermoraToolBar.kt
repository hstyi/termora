package app.termora

import app.termora.Application.ohMyJson
import app.termora.db.Database
import com.formdev.flatlaf.extras.components.FlatTabbedPane
import com.formdev.flatlaf.util.SystemInfo
import com.jetbrains.WindowDecorations
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionContainerFactory
import org.jdesktop.swingx.action.ActionManager
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Box
import javax.swing.JToolBar

class TermoraToolBar(
    private val titleBar: WindowDecorations.CustomTitleBar,
    private val tabbedPane: FlatTabbedPane
) {
    private val properties by lazy { Database.instance.properties }
    private val toolbar by lazy { MyToolBar().apply { rebuild(this) } }

    private val shownActions = mutableListOf<String>()

    fun getJToolBar(): JToolBar {
        return toolbar
    }


    fun getShownActions(): List<String> {
        return shownActions
    }

    fun getAllActions(): List<String> {
        return listOf(
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

        shownActions.clear()
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

        val actions = ohMyJson.runCatching {
            ohMyJson.decodeFromString<List<String>>(
                properties.getString(
                    "Termora.ToolBar.Actions",
                    StringUtils.EMPTY
                )
            )
        }.getOrNull() ?: getAllActions()


        // update btn
        val updateBtn = actionContainerFactory.createButton(actionManager.getAction(Actions.APP_UPDATE))
        updateBtn.isVisible = updateBtn.isEnabled
        updateBtn.addChangeListener { updateBtn.isVisible = updateBtn.isEnabled }
        toolbar.add(updateBtn)

        for (action in actions) {
            actionManager.getAction(action)?.let {
                toolbar.add(actionContainerFactory.createButton(it))
                shownActions.add(action)
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