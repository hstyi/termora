package app.termora.findeverywhere

import app.termora.DynamicIcon
import com.formdev.flatlaf.FlatLaf
import org.jdesktop.swingx.action.AbstractActionExt
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.Icon
import javax.swing.SwingUtilities

open class ActionFindEverywhereResult(private val action: Action) : FindEverywhereResult {
    private val isState: Boolean
        get() {
            val isState = action.getValue(AbstractActionExt.IS_STATE)
            return (isState is Boolean && isState) || (action is AbstractActionExt && action.isStateAction)
        }
    private val isSelected: Boolean
        get() {
            var isSelected = action.getValue(Action.SELECTED_KEY)
            if (isSelected == null) {
                isSelected = false
            }
            return isSelected is Boolean && isSelected
        }

    override fun actionPerformed(e: ActionEvent) {
        if (isState) {
            action.putValue(Action.SELECTED_KEY, !isSelected)
        }
        SwingUtilities.invokeLater { action.actionPerformed(e) }
    }

    override fun getIcon(isSelected: Boolean): Icon {
        val icon = action.getValue(Action.SMALL_ICON)
        if (isSelected) {
            if (!FlatLaf.isLafDark()) {
                if (icon is DynamicIcon) {
                    return icon.dark
                }
            }
        }
        return if (icon is Icon) icon else super.getIcon(isSelected)
    }

    override fun toString(): String {
        val text = action.getValue(Action.NAME)
        return if (text is String) text else action.toString()
    }
}