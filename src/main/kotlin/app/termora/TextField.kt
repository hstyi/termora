package app.termora

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.extras.components.*
import com.formdev.flatlaf.ui.FlatTextBorder
import org.apache.commons.lang3.StringUtils
import java.awt.Component
import java.awt.event.*
import java.text.ParseException
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.SpinnerNumberModel
import javax.swing.text.AttributeSet
import javax.swing.text.DefaultFormatterFactory
import javax.swing.text.PlainDocument


open class OutlineTextField(var maxLength: Int = Int.MAX_VALUE) : FlatTextField() {
    init {
        this.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                outline = null
            }
        })


        document = object : PlainDocument() {
            override fun insertString(offs: Int, str: String?, a: AttributeSet?) {
                if (str != null && str.length + length <= maxLength) {
                    super.insertString(offs, str, a)
                }
            }
        }
    }
}

class OutlineTextArea : FlatTextArea() {
    init {
        border = FlatTextBorder()

        addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                border = FlatTextBorder()
            }

            override fun focusGained(e: FocusEvent) {
                border = FlatTextBorder()
            }
        })
    }
}

class OutlineComboBox<T> : FlatComboBox<T>() {
    init {
        addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                putClientProperty(FlatClientProperties.OUTLINE, null)
            }
        }
    }
}

class FixedLengthTextArea(var maxLength: Int = Int.MAX_VALUE) : FlatTextArea() {
    init {
        document = object : PlainDocument() {
            override fun insertString(offs: Int, str: String?, a: AttributeSet?) {
                if (str != null && str.length + length <= maxLength) {
                    super.insertString(offs, str, a)
                }
            }
        }
    }
}


class OutlinePasswordField(
    var maxLength: Int = Int.MAX_VALUE,
    var allowSpace: Boolean = false
) : FlatPasswordField() {
    init {
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                outline = null
            }
        })

        document = object : PlainDocument() {
            override fun insertString(offs: Int, str: String?, a: AttributeSet?) {
                if (str != null && str.length + length <= maxLength) {
                    val text = if (allowSpace) str else str.replace(StringUtils.SPACE, StringUtils.EMPTY)
                    if (text.isNotEmpty()) {
                        super.insertString(offs, text, a)
                    }
                }
            }
        }

        styleMap = mapOf(
            "showRevealButton" to true
        )

        putClientProperty("JPasswordField.cutCopyAllowed", true)
    }
}

open class OutlineFormattedTextField : FlatFormattedTextField() {
    init {
        this.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                outline = null
            }
        })
    }
}

open class EmailFormattedTextField(var maxLength: Int = Int.MAX_VALUE) : OutlineFormattedTextField() {
    init {
        formatterFactory = DefaultFormatterFactory(object : AbstractFormatter() {
            private val regex = Regex(
                ("^(?=.{1,64}@)[A-Za-z0-9\\+_-]+(\\.[A-Za-z0-9\\+_-]+)*@"
                        + "[^-][A-Za-z0-9\\+-]+(\\.[A-Za-z0-9\\+-]+)*(\\.[A-Za-z]{2,})$")
            )

            override fun stringToValue(text: String?): Any {
                if (text.isNullOrBlank()) return String()
                if (!regex.matches(text)) throw ParseException("Not an email", 0)
                return text
            }

            override fun valueToString(value: Any?): String {
                return value?.toString() ?: String()
            }

        })


        document = object : PlainDocument() {
            override fun insertString(offs: Int, str: String?, a: AttributeSet?) {
                if (str != null && str.length + length <= maxLength) {
                    super.insertString(offs, str, a)
                }
            }
        }
    }
}


open class NumberSpinner(
    value: Int,
    minimum: Int,
    maximum: Int,
) : FlatSpinner() {
    init {
        val snm = SpinnerNumberModel()
        snm.minimum = minimum
        snm.maximum = maximum
        snm.value = value
        model = snm
    }

    override fun getModel(): SpinnerNumberModel {
        return super.getModel() as SpinnerNumberModel
    }
}

class PortSpinner(value: Int = 22) : NumberSpinner(value, 0, UShort.MAX_VALUE.toInt()) {
    init {
        setEditor(NumberEditor(this, "#"))
    }
}

class IntSpinner(value: Int, minimum: Int = Int.MIN_VALUE, maximum: Int = Int.MAX_VALUE) :
    NumberSpinner(value, minimum, maximum) {
    init {
        setEditor(NumberEditor(this, "#"))
    }
}

class YesOrNoComboBox : JComboBox<Boolean>() {
    init {
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                return super.getListCellRendererComponent(
                    list,
                    if (value == true) I18n.getString("termora.yes") else I18n.getString("termora.no"),
                    index,
                    isSelected,
                    cellHasFocus
                )
            }
        }

        addItem(true)
        addItem(false)
    }
}