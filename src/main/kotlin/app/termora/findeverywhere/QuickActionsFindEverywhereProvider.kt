package app.termora.findeverywhere

import app.termora.Actions
import app.termora.I18n

import org.jdesktop.swingx.action.ActionManager

class QuickActionsFindEverywhereProvider : FindEverywhereProvider {
    private val actions = listOf(
        Actions.KEY_MANAGER,
        Actions.KEYWORD_HIGHLIGHT,
        Actions.MULTIPLE,
    )

    override fun find(pattern: String): List<FindEverywhereResult> {
        val actionManager = ActionManager.getInstance()
        return actions
            .mapNotNull { actionManager.getAction(it) }
            .map { ActionFindEverywhereResult(it) }
    }


    override fun order(): Int {
        return Integer.MIN_VALUE + 3
    }

    override fun group(): String {

        return I18n.getString("termora.find-everywhere.groups.tools")
    }


}