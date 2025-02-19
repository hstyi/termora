package app.termora.findeverywhere

import app.termora.Icons
import java.awt.event.ActionListener
import javax.swing.Icon

interface FindEverywhereResult : ActionListener {

    fun getIcon(isSelected: Boolean): Icon = Icons.empty

    fun getText(isSelected: Boolean) = toString()

}