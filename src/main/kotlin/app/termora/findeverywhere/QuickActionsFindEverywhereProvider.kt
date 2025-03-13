package app.termora.findeverywhere

import app.termora.Actions
import app.termora.I18n
import app.termora.WindowScope
import app.termora.actions.MultipleAction

import org.jdesktop.swingx.action.ActionManager

class QuickActionsFindEverywhereProvider(private val windowScope: WindowScope) : FindEverywhereProvider {
    private val actions = listOf(
        Actions.KEY_MANAGER,
        Actions.KEYWORD_HIGHLIGHT,
        MultipleAction.MULTIPLE,
    )

    override fun find(pattern: String): List<FindEverywhereResult> {
        val actionManager = ActionManager.getInstance()
        val results = ArrayList<FindEverywhereResult>()
        for (action in actions) {
            val ac = actionManager.getAction(action)
            if (ac == null) {
                if (action == MultipleAction.MULTIPLE) {
                    results.add(ActionFindEverywhereResult(MultipleAction.getInstance(windowScope)))
                }
            } else {
                results.add(ActionFindEverywhereResult(ac))
            }
        }
        return results
    }


    override fun order(): Int {
        return Integer.MIN_VALUE + 3
    }

    override fun group(): String {

        return I18n.getString("termora.find-everywhere.groups.tools")
    }


}