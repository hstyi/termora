package app.termora.sftp

import app.termora.actions.AnActionEvent
import org.apache.commons.lang3.StringUtils
import java.util.*

class SFTPActionEvent(
    source: Any,
    val hostId: String,
    event: EventObject
) : AnActionEvent(source, StringUtils.EMPTY, event)