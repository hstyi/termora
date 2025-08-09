package app.termora

import com.formdev.flatlaf.util.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class ApplePressAndHoldEnabledApplicationRunnerExtension private constructor() : ApplicationRunnerExtension {
    companion object {
        val instance = ApplePressAndHoldEnabledApplicationRunnerExtension()
    }

    override fun ready() {
        if (SystemInfo.isMacOS.not()) return

        swingCoroutineScope.launch(Dispatchers.IO) {
            Runtime.getRuntime()
                .exec(arrayOf("defaults", "write", "app.termora", "ApplePressAndHoldEnabled", "-bool", "false"))
                .waitFor()
        }
    }
}