package app.termora.actions

import app.termora.terminal.DataKey

/**
 * 数据提供者，从 [AnActionEvent.source] 开始搜索然后依次 [getData] 获取数据
 */
interface DataProvider {
    companion object {
        val EMPTY = object : DataProvider {
            override fun <T : Any> getData(dataKey: DataKey<T>): T? {
                return null
            }
        }
    }

    /**
     * 数据提供
     */
    fun <T : Any> getData(dataKey: DataKey<T>): T?
}