package app.termora.plugin

interface Extension {
    /**
     * 越小越靠前
     */
    fun ordered(): Long = Long.MAX_VALUE
}