package app.termora

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.swing.Swing
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Window
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import kotlin.reflect.KClass

val swingCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

@Suppress("UNCHECKED_CAST")
open class Scope(
    private val beans: MutableMap<KClass<*>, Any> = ConcurrentHashMap(),
    private val properties: MutableMap<String, Any> = ConcurrentHashMap()
) : Disposable {


    fun <T : Any> get(clazz: KClass<T>): T {
        return beans[clazz] as T
    }


    fun <T : Any> getOrCreate(clazz: KClass<T>, create: () -> T): T {

        if (beans.containsKey(clazz)) {
            return get(clazz)
        }

        synchronized(clazz) {
            if (beans.containsKey(clazz)) {
                return get(clazz)
            }

            val instance = create.invoke()
            beans[clazz] = instance

            if (instance is Disposable) {
                Disposer.register(this, instance)
            }
            return instance
        }

    }


    fun putBoolean(name: String, value: Boolean) {
        properties[name] = value
    }

    fun getBoolean(name: String, defaultValue: Boolean): Boolean {
        return properties[name]?.toString()?.toBoolean() ?: defaultValue
    }

    fun putAny(name: String, value: Any) {
        properties[name] = value
    }

    fun getAny(name: String, defaultValue: Any): Any {
        return properties[name]?.toString() ?: defaultValue
    }

    fun getAnyOrNull(name: String): Any? {
        return properties[name]
    }


    override fun dispose() {
        beans.clear()
    }
}


class ApplicationScope private constructor() : Scope() {

    private val scopes = mutableMapOf<Any, WindowScope>()

    companion object {
        private val log = LoggerFactory.getLogger(ApplicationScope::class.java)
        private val instance by lazy { ApplicationScope() }

        fun forApplicationScope(): ApplicationScope {
            return instance
        }

        fun forWindowScope(frame: TermoraFrame): WindowScope {
            return forApplicationScope().forWindowScope(frame)
        }

        fun forWindowScope(container: Component): WindowScope {
            val frame = getFrameForComponent(container)
                ?: throw IllegalStateException("Unexpected owner in $container")
            return forWindowScope(frame)
        }

        fun windowScopes(): List<WindowScope> {
            return forApplicationScope().windowScopes()
        }

        private fun getFrameForComponent(component: Component): TermoraFrame? {
            if (component is TermoraFrame) {
                return component
            }

            var owner = SwingUtilities.getWindowAncestor(component) as Component?
            if (owner is TermoraFrame) {
                return owner
            }

            if (owner == null) {
                owner = component
            }

            while (owner != null) {

                if (owner is JPopupMenu) {
                    owner = owner.invoker
                    if (owner is TermoraFrame) {
                        return owner
                    }
                    continue
                }

                owner = owner.parent
                if (owner is TermoraFrame) {
                    return owner
                }
            }

            return null
        }

    }


    private fun forWindowScope(frame: TermoraFrame): WindowScope {
        val windowScope = scopes.getOrPut(frame) { WindowScope(frame) }
        Disposer.register(windowScope, object : Disposable {
            override fun dispose() {
                scopes.remove(frame)
            }
        })

        return windowScope
    }

    fun windowScopes(): List<WindowScope> {
        if (scopes.isEmpty()) return emptyList()
        return scopes.values.toList()
    }

    override fun dispose() {
        if (log.isInfoEnabled) {
            log.info("ApplicationScope disposed")
        }
        swingCoroutineScope.cancel()
        super.dispose()
    }

}


class WindowScope(
    val window: Window,
) : Scope() {
    companion object {
        private val log = LoggerFactory.getLogger(WindowScope::class.java)
    }

    override fun dispose() {
        if (log.isInfoEnabled) {
            log.info("WindowScope disposed")
        }
        super.dispose()
    }
}