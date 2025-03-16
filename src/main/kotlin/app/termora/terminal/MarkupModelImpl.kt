package app.termora.terminal


open class MarkupModelImpl(private val terminal: Terminal) : MarkupModel {
    private val highlighters = mutableMapOf<Int, MutableList<Highlighter>>()

    init {
        terminal.getTerminalModel().addDataListener(object : DataListener {
            override fun onChanged(key: DataKey<*>, data: Any) {
                if (key != DocumentImpl.OverflowLines) return
                if (highlighters.isEmpty()) return
                val row = data as Int
                // 因为第 N 行被删除了，所以这里要删除这一行的荧光笔
                for (i in 0 until row) {
                    terminal.getMarkupModel().removeAllHighlightersInLine(0)
                }
            }
        })
    }

    override fun addHighlighter(highlighter: Highlighter) {
        val range = highlighter.getHighlighterRange()
        highlighters.getOrPut(range.start.y) { mutableListOf() }.addLast(highlighter)
        if (range.start.y != range.end.y) {
            for (i in range.start.y + 1..range.end.y) {
                highlighters.getOrPut(i) { mutableListOf() }.addLast(highlighter)
            }
        }
    }

    override fun removeHighlighter(highlighter: Highlighter) {
        val range = highlighter.getHighlighterRange()
        if (highlighters.containsKey(range.start.y)) {
            highlighters.getValue(range.start.y).remove(highlighter)
        }
        if (range.start.y != range.end.y) {
            for (i in range.start.y + 1..range.end.y) {
                highlighters.getValue(i).remove(highlighter)
            }
        }
    }

    override fun removeAllHighlighters(tag: Int) {
        if (tag == 0) {
            if (highlighters.isEmpty()) return
            highlighters.clear()
        } else {
            val iterator = highlighters.entries.iterator()
            while (iterator.hasNext()) {
                val e = iterator.next().value
                e.removeAll { it.getTag() == tag }
                if (e.isEmpty()) {
                    iterator.remove()
                }
            }
        }

    }

    override fun removeAllHighlightersInLine(row: Int) {
        highlighters.remove(row)
    }


    override fun getHighlighters(position: Position): List<Highlighter> {
        if (highlighters.containsKey(position.y)) {
            return highlighters.getValue(position.y).filter {
                SelectionModelImpl.isPointInsideArea(
                    it.getHighlighterRange().start,
                    it.getHighlighterRange().end,
                    position.x,
                    position.y,
                    terminal.getTerminalModel().getCols()
                )
            }
        }
        return emptyList()
    }


    override fun getTerminal(): Terminal {
        return terminal
    }
}

