package app.termora.sftp

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.provider.local.LocalFile
import java.io.File


fun FileObject.absolutePathString(): String {
    var text = name.path
    if (this is LocalFile && SystemUtils.IS_OS_WINDOWS) {
        text = this.name.toString()
        text = StringUtils.removeStart(text, "file:///")
        text = StringUtils.replace(text, "/", File.separator)
    }
    return text
}
