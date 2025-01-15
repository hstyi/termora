package app.termora.actions

import org.jdesktop.swingx.action.BoundAction
import java.awt.event.ActionEvent
import javax.swing.Icon

abstract class AnAction : BoundAction {


    constructor() : super()
    constructor(icon: Icon) : super() {
        super.putValue(SMALL_ICON, icon)
    }

    constructor(name: String?) : super(name)
    constructor(name: String?, icon: Icon?) : super(name, icon)


    final override fun actionPerformed(evt: ActionEvent) {
        if (evt is AnActionEvent) {
            actionPerformed(evt)
        } else {
            actionPerformed(AnActionEvent(evt.source, evt.actionCommand, evt))
        }
    }


    protected abstract fun actionPerformed(evt: AnActionEvent)

}