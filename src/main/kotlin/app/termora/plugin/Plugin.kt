package app.termora.plugin

import app.termora.ApplicationRunnerExtension
import app.termora.DynamicIcon

/**
 * 插件类
 *
 * 此插件类不应该做任何业务操作，因为在插件初始化时还程序还尚未初始化完成。如果要初始化数据应该实现 [ApplicationRunnerExtension.ready] 扩展
 *
 * 实现类必须有一个默认构造器
 */
interface Plugin {

    /**
     * 作者
     */
    fun getAuthor(): String

    /**
     * 图标
     */
    fun getIcon(): DynamicIcon

    /**
     * 名称
     */
    fun getName(): String

    /**
     * 描述
     */
    fun getDescription(): String


    /**
     * 获取扩展，会多次调用
     */
    fun <T : Extension> getExtensions(clazz: Class<T>): List<T>
}