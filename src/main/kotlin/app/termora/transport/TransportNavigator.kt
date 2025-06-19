package app.termora.transport

import java.beans.PropertyChangeListener
import java.nio.file.Path

interface TransportNavigator {

    val loading: Boolean
    val workdir: Path?

    fun navigateTo(destination: Path): Boolean

    fun addPropertyChangeListener(propertyName: String, listener: PropertyChangeListener)
    fun removePropertyChangeListener(propertyName: String, listener: PropertyChangeListener)

    fun getHistory(): List<Path>

    fun canRedo(): Boolean
    fun canUndo(): Boolean
    fun back()
    fun forward()
}