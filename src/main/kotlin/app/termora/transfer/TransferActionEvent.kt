package app.termora.transfer

import app.termora.actions.AnActionEvent
import org.apache.commons.lang3.StringUtils
import java.util.*

class TransferActionEvent(
    source: Any,
    val hostId: String,
    event: EventObject
) : AnActionEvent(source, StringUtils.EMPTY, event)