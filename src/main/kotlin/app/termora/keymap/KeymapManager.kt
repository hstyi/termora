package app.termora.keymap

import app.termora.ApplicationScope
import app.termora.Database
import app.termora.DialogWrapper
import app.termora.Disposable
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import app.termora.findeverywhere.FindEverywhereAction
import com.formdev.flatlaf.util.SystemInfo
import org.apache.commons.lang3.StringUtils
import org.jdesktop.swingx.action.ActionManager
import org.slf4j.LoggerFactory
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.KeyStroke

class KeymapManager private constructor() : Disposable {

    companion object {
        private val log = LoggerFactory.getLogger(KeymapManager::class.java)

        fun getInstance(): KeymapManager {
            return ApplicationScope.forApplicationScope()
                .getOrCreate(KeymapManager::class) { KeymapManager() }
        }
    }

    private val keymapKeyEventDispatcher = KeymapKeyEventDispatcher()
    private val myKeyEventDispatcher = MyKeyEventDispatcher()
    private val database get() = Database.getDatabase()
    private val keymaps = linkedMapOf<String, Keymap>()
    private val activeKeymap get() = database.properties.getString("Keymap.Active")
    private val keyboardFocusManager by lazy { KeyboardFocusManager.getCurrentKeyboardFocusManager() }

    init {
        keyboardFocusManager.addKeyEventDispatcher(keymapKeyEventDispatcher)
        keyboardFocusManager.addKeyEventDispatcher(myKeyEventDispatcher)

        try {
            for (keymap in database.getKeymaps()) {
                keymaps[keymap.name] = keymap
            }
        } catch (e: Exception) {
            if (log.isErrorEnabled) {
                log.error(e.message, e)
            }
        }

        MacOSKeymap.getInstance().let {
            keymaps[it.name] = it
        }

        WindowsKeymap.getInstance().let {
            keymaps[it.name] = it
        }

    }


    fun getActiveKeymap(): Keymap {
        val name = activeKeymap
        if (name != null) {
            val keymap = getKeymap(name)
            if (keymap != null) {
                return keymap
            }
        }

        return if (SystemInfo.isMacOS) {
            MacOSKeymap.getInstance()
        } else {
            WindowsKeymap.getInstance()
        }
    }

    fun getKeymap(name: String): Keymap? {
        return keymaps[name]
    }

    fun getKeymaps(): List<Keymap> {
        return keymaps.values.toList()
    }

    fun addKeymap(keymap: Keymap) {
        keymaps.putFirst(keymap.name, keymap)
        database.addKeymap(keymap)
    }

    fun removeKeymap(name: String) {
        keymaps.remove(name)
        database.removeKeymap(name)
    }

    private inner class KeymapKeyEventDispatcher : KeyEventDispatcher {

        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            if (e.isConsumed || e.id != KeyEvent.KEY_PRESSED || e.modifiersEx == 0) {
                return false
            }

            val keyStroke = KeyStroke.getKeyStrokeForEvent(e)
            val component = e.source

            if (component is JComponent) {
                // 如果这个键已经被组件注册了，那么忽略
                if (component.getConditionForKeyStroke(keyStroke) != JComponent.UNDEFINED_CONDITION) {
                    return false
                }
            }


            val shortcuts = getActiveKeymap()
            val actionIds = shortcuts.getActionIds(KeyShortcut(keyStroke))
            if (actionIds.isEmpty()) {
                return false
            }

            val focusedWindow = keyboardFocusManager.focusedWindow
            if (focusedWindow is DialogWrapper) {
                if (!focusedWindow.processGlobalKeymap) {
                    return false
                }
            } else if (focusedWindow is JDialog) {
                return false
            }


            val evt = AnActionEvent(e.source, StringUtils.EMPTY, e)
            for (actionId in actionIds) {
                val action = ActionManager.getInstance().getAction(actionId) ?: continue
                if (!action.isEnabled) {
                    continue
                }
                action.actionPerformed(evt)
                if (evt.isConsumed) {
                    return true
                }
            }

            return false
        }

    }

    private inner class MyKeyEventDispatcher : KeyEventDispatcher {
        // double shift
        private var lastTime = -1L

        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            if (e.keyCode == KeyEvent.VK_SHIFT && e.id == KeyEvent.KEY_PRESSED) {
                val owner = AnActionEvent(e.source, StringUtils.EMPTY, e).getData(DataProviders.TermoraFrame)
                    ?: return false
                if (keyboardFocusManager.focusedWindow == owner) {
                    val now = System.currentTimeMillis()
                    if (now - 250 < lastTime) {
                        app.termora.actions.ActionManager.getInstance()
                            .getAction(FindEverywhereAction.FIND_EVERYWHERE)
                            ?.actionPerformed(AnActionEvent(e.source, StringUtils.EMPTY, e))
                    }
                    lastTime = now
                }
            } else if (e.keyCode != KeyEvent.VK_SHIFT) { // 如果不是 Shift 键，那么就阻断了连续性，重置时间
                lastTime = -1
            }
            return false

        }

    }


    override fun dispose() {
        keyboardFocusManager.removeKeyEventDispatcher(keymapKeyEventDispatcher)
        keyboardFocusManager.removeKeyEventDispatcher(myKeyEventDispatcher)
    }
}