package app.termora.plugin

import javax.swing.Icon

/**
 * 插件
 */
interface Plugin {
    /**
     * 插件版本
     */
    fun getVersion(): String

    /**
     * 作者
     */
    fun getAuthor(): String

    /**
     * 图标
     */
    fun getIcon(): Icon

    /**
     * 名称
     */
    fun getName(): String

    /**
     * 描述
     */
    fun getDescription(): String

    /**
     * 扩展点，会多次调用
     */
    fun getExtensions(): List<Extension>
}