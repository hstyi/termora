package app.termora

import java.util.*

class OpenHostActionEvent(source: Any, val host: Host, event: EventObject) :
    AnActionEvent(source, ACTION_PERFORMED, String(), event)