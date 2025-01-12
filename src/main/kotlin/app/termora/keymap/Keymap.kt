package app.termora.keymap

import app.termora.Application.ohMyJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

open class Keymap(
    val name: String,
    /**
     * 当 [getShortcut] [getActionIds] 获取不到的时候从父里面获取
     */
    private val parent: Keymap?,
    val isReadonly: Boolean = false,
) {

    private val shortcuts = mutableMapOf<Shortcut, MutableList<String>>()

    open fun addShortcut(actionId: String, shortcut: Shortcut) {
        val actionIds = shortcuts.getOrPut(shortcut) { mutableListOf() }
        actionIds.removeIf { it == actionId }
        actionIds.add(actionId)
    }

    open fun removeAllActionShortcuts(actionId: Any) {
        val iterator = shortcuts.iterator()
        while (iterator.hasNext()) {
            val shortcut = iterator.next()
            shortcut.value.removeIf { it == actionId }
            if (shortcut.value.isEmpty()) {
                iterator.remove()
            }
        }
    }

    open fun getShortcut(actionId: Any): List<Shortcut> {
        val shortcuts = mutableListOf<Shortcut>()
        for (e in this.shortcuts.entries) {
            if (e.value.contains(actionId)) {
                shortcuts.add(e.key)
            }
        }
        if (shortcuts.isEmpty()) {
            parent?.getShortcut(actionId)?.let { shortcuts.addAll(it) }
        }
        return shortcuts
    }

    open fun getShortcuts(): Map<Shortcut, List<String>> {
        val shortcuts = mutableMapOf<Shortcut, List<String>>()
        shortcuts.putAll(this.shortcuts)
        parent?.let { shortcuts.putAll(it.getShortcuts()) }
        return shortcuts
    }

    open fun getActionIds(shortcut: Shortcut): List<String> {
        val actionIds = mutableListOf<String>()
        shortcuts[shortcut]?.let { actionIds.addAll(it) }
        if (actionIds.isEmpty()) {
            parent?.getActionIds(shortcut)?.let { actionIds.addAll(it) }
        }
        return actionIds
    }


    fun toJSON(): String {
        return ohMyJson.encodeToString(buildJsonObject {
            put("name", name)
            put("readonly", isReadonly)
            parent?.let { put("parent", it.name) }
            put("shortcuts", buildJsonArray {
                for (entry in shortcuts.entries) {
                    add(buildJsonObject {
                        put("keyboard", entry.key.isKeyboard())
                        if (entry.key is KeyShortcut) {
                            put("keyStroke", (entry.key as KeyShortcut).keyStroke.toString())
                        }
                        put("actionIds", ohMyJson.encodeToJsonElement(entry.value))
                    })
                }
            })
        })
    }

}