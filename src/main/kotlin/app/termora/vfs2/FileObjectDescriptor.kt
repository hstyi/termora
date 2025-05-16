package app.termora.vfs2

import javax.swing.Icon

/**
 * 文件描述
 */
interface FileObjectDescriptor {

    /**
     * 图标
     */
    fun getIcon(width: Int, height: Int): Icon? = null


}