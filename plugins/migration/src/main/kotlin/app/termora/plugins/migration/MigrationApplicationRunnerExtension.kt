package app.termora.plugins.migration

import app.termora.Application
import app.termora.ApplicationRunnerExtension
import org.apache.commons.io.FileUtils
import java.io.File

class MigrationApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
    companion object {
        val instance by lazy { MigrationApplicationRunnerExtension() }
    }

    override fun ready() {
        val file = getDatabaseFile()
        if (file.exists().not()) return

        // 如果数据库文件存在，那么需要迁移文件

    }


    private fun getDatabaseFile(): File {
        return FileUtils.getFile(Application.getBaseDataDir(), "storage")
    }
}