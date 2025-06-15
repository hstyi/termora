package app.termora.findeverywhere

import app.termora.I18n
import app.termora.Scope

class SettingsFindEverywhereProvider : FindEverywhereProvider {


    override fun find(pattern: String, scope: Scope): List<FindEverywhereResult> {
        return emptyList()
    }


    override fun group(): String {
        return I18n.getString("termora.find-everywhere.groups.settings")
    }

}