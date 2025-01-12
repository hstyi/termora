package app.termora.actions

import app.termora.terminal.DataKey

class DataProviderSupport : DataProvider {
    private val map = mutableMapOf<DataKey<*>, Any>()

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        if (map.containsKey(dataKey)) {
            @Suppress("UNCHECKED_CAST")
            return map[dataKey] as T
        }

        return null
    }

    fun <T : Any> addData(key: DataKey<T>, data: T) {
        map[key] = data
    }

    fun removeData(key: DataKey<*>) {
        map.remove(key)
    }
}