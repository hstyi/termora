package app.termora.plugin

internal class PluginAnchor(private val anchor: String) : Comparable<PluginAnchor> {
    companion object {
        val LAST = PluginAnchor("last")
        val FIRST = PluginAnchor("first")
    }

    override fun compareTo(other: PluginAnchor): Int {
return 0
    }

}