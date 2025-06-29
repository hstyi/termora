package app.termora.tree

interface Filter {

    fun filter(node: Any): Boolean

    fun canFilter() = true
}