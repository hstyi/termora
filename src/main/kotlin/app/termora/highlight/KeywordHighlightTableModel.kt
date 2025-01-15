package app.termora.highlight

import javax.swing.table.DefaultTableModel

class KeywordHighlightTableModel : DefaultTableModel() {
    private val rows get() = KeywordHighlightManager.getInstance().getKeywordHighlights()

    override fun isCellEditable(row: Int, column: Int): Boolean {
        return false
    }

    fun getKeywordHighlight(row: Int): KeywordHighlight {
        return rows[row]
    }

    override fun getRowCount(): Int {
        return rows.size
    }

    override fun getValueAt(row: Int, column: Int): Any {
        val highlight = getKeywordHighlight(row)
        return when (column) {
            0 -> highlight
            1 -> highlight
            2 -> highlight.description
            else -> String()
        }
    }
}