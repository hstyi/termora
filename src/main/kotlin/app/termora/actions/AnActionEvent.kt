package app.termora.actions

import app.termora.terminal.DataKey
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.ActionEvent
import java.util.*
import javax.swing.JPopupMenu

open class AnActionEvent(
    source: Any, command: String,
    val event: EventObject
) : ActionEvent(source, AN_ACTION_PERFORMED, command), DataProvider {

    companion object {
        const val AN_ACTION_PERFORMED = ACTION_PERFORMED + 1
    }


    val window: Window
        get() = getData(DataProviders.TermoraFrame)
            ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow


    public override fun consume() {
        super.consumed = true
    }

    public override fun isConsumed(): Boolean {
        return super.isConsumed()
    }


    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        val source = getSource()
        if (source !is Component) {
            if (source is DataProvider) {
                return source.getData(dataKey)
            }
            return null
        } else {
            var c = source as Component?
            while (c != null) {
                if (c is DataProvider) {
                    val data = c.getData(dataKey)
                    if (data != null) {
                        return data
                    }
                }
                val p = c.parent
                c = if (p == null && c is JPopupMenu) {
                    c.invoker
                } else {
                    p
                }
            }
            return null
        }
    }
}