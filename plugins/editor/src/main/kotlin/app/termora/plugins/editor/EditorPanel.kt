package app.termora.plugins.editor

import app.termora.DocumentAdaptor
import app.termora.DynamicColor
import app.termora.EnableManager
import app.termora.Icons
import app.termora.database.DatabaseManager
import com.formdev.flatlaf.FlatLaf
import com.formdev.flatlaf.extras.components.FlatTextField
import com.formdev.flatlaf.extras.components.FlatToolBar
import kotlinx.serialization.json.Json
import org.apache.commons.io.FilenameUtils
import org.dom4j.io.OutputFormat
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rtextarea.RTextScrollPane
import org.fife.ui.rtextarea.SearchContext
import org.fife.ui.rtextarea.SearchEngine
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import javax.swing.*
import javax.swing.SwingConstants.VERTICAL
import javax.swing.event.DocumentEvent
import kotlin.math.max

class EditorPanel(private val window: JDialog, private val file: File) : JPanel(BorderLayout()) {

    companion object {
        private val log = LoggerFactory.getLogger(EditorPanel::class.java)
    }

    private var text = file.readText(Charsets.UTF_8)
    private val layeredPane = LayeredPane()

    private val textArea = RSyntaxTextArea()
    private val scrollPane = RTextScrollPane(textArea)
    private val findPanel = FlatToolBar().apply { isFloatable = false }
    private val toolbar = FlatToolBar().apply { isFloatable = false }
    private val searchTextField = FlatTextField()
    private val closeFindPanelBtn = JButton(Icons.close)
    private val nextBtn = JButton(Icons.down)
    private val prevBtn = JButton(Icons.up)
    private val context = SearchContext()
    private val softWrapBtn = JToggleButton(Icons.softWrap)
    private val scrollUpBtn = JButton(Icons.scrollUp)
    private val scrollEndBtn = JButton(Icons.scrollDown)
    private val prettyBtn = JButton(Icons.reformatCode)

    private val enableManager get() = EnableManager.getInstance()
    private val prettyJson = Json {
        prettyPrint = true
    }

    init {
        initView()
        initEvents()
    }


    private fun initView() {
        textArea.font = textArea.font.deriveFont(DatabaseManager.getInstance().terminal.fontSize.toFloat())
        textArea.text = text
        textArea.antiAliasingEnabled = true
        softWrapBtn.isSelected = enableManager.getFlag("Plugins.editor.softWrap", false)

        val theme = if (FlatLaf.isLafDark())
            Theme.load(javaClass.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"))
        else
            Theme.load(javaClass.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/idea.xml"))

        theme.apply(textArea)

        val extension = FilenameUtils.getExtension(file.name)?.lowercase()
        textArea.syntaxEditingStyle = when (extension) {
            "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA
            "kt" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN
            "properties" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE
            "cpp", "c++" -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS
            "c" -> SyntaxConstants.SYNTAX_STYLE_C
            "cs" -> SyntaxConstants.SYNTAX_STYLE_CSHARP
            "css" -> SyntaxConstants.SYNTAX_STYLE_CSS
            "html", "htm", "htmlx" -> SyntaxConstants.SYNTAX_STYLE_HTML
            "js" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
            "ts" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT
            "xml", "svg" -> SyntaxConstants.SYNTAX_STYLE_XML
            "yaml", "yml" -> SyntaxConstants.SYNTAX_STYLE_YAML
            "sh", "shell" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL
            "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL
            "bat" -> SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH
            "py" -> SyntaxConstants.SYNTAX_STYLE_PYTHON
            "php" -> SyntaxConstants.SYNTAX_STYLE_PHP
            "lua" -> SyntaxConstants.SYNTAX_STYLE_LUA
            "less" -> SyntaxConstants.SYNTAX_STYLE_LESS
            "jsp" -> SyntaxConstants.SYNTAX_STYLE_JSP
            "json" -> SyntaxConstants.SYNTAX_STYLE_JSON
            "ini" -> SyntaxConstants.SYNTAX_STYLE_INI
            "hosts" -> SyntaxConstants.SYNTAX_STYLE_HOSTS
            "go" -> SyntaxConstants.SYNTAX_STYLE_GO
            "dtd" -> SyntaxConstants.SYNTAX_STYLE_DTD
            "dart" -> SyntaxConstants.SYNTAX_STYLE_DART
            "csv" -> SyntaxConstants.SYNTAX_STYLE_CSV
            "md" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN
            else -> SyntaxConstants.SYNTAX_STYLE_NONE
        }

        // 只有 JSON 才可以格式化
        prettyBtn.isVisible = textArea.syntaxEditingStyle == SyntaxConstants.SYNTAX_STYLE_JSON ||
                textArea.syntaxEditingStyle == SyntaxConstants.SYNTAX_STYLE_XML

        textArea.discardAllEdits()

        scrollPane.border = BorderFactory.createMatteBorder(0, 0, 0, 1, DynamicColor.BorderColor)

        findPanel.isVisible = false
        findPanel.isOpaque = true
        findPanel.background = DynamicColor("window")

        searchTextField.background = findPanel.background
        searchTextField.padding = Insets(0, 4, 0, 0)
        searchTextField.border = BorderFactory.createEmptyBorder()

        findPanel.add(searchTextField)
        findPanel.add(prevBtn)
        findPanel.add(nextBtn)
        findPanel.add(closeFindPanelBtn)
        findPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 1, 0, DynamicColor.BorderColor),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        )

        toolbar.orientation = VERTICAL
        toolbar.add(scrollUpBtn)
        toolbar.add(prettyBtn)
        toolbar.add(softWrapBtn)
        toolbar.add(scrollEndBtn)

        val viewPanel = JPanel(BorderLayout())
        viewPanel.add(scrollPane, BorderLayout.CENTER)
        viewPanel.add(toolbar, BorderLayout.EAST)
        viewPanel.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)

        layeredPane.add(findPanel, JLayeredPane.MODAL_LAYER as Any)
        layeredPane.add(viewPanel, JLayeredPane.DEFAULT_LAYER as Any)

        add(layeredPane, BorderLayout.CENTER)
    }


    private fun initEvents() {

        window.addWindowListener(object : WindowAdapter() {
            override fun windowOpened(e: WindowEvent?) {
                scrollPane.verticalScrollBar.value = 0
                window.removeWindowListener(this)
            }
        })

        softWrapBtn.addActionListener {
            enableManager.getFlag("Plugins.editor.softWrap", softWrapBtn.isSelected)
            textArea.lineWrap = softWrapBtn.isSelected
        }

        scrollUpBtn.addActionListener { scrollPane.verticalScrollBar.value = 0 }
        scrollEndBtn.addActionListener { scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.maximum }

        textArea.inputMap.put(
            KeyStroke.getKeyStroke(KeyEvent.VK_S, toolkit.menuShortcutKeyMaskEx),
            "Save"
        )
        textArea.inputMap.put(
            KeyStroke.getKeyStroke(KeyEvent.VK_F, toolkit.menuShortcutKeyMaskEx),
            "Find"
        )
        textArea.inputMap.put(
            KeyStroke.getKeyStroke(KeyEvent.VK_F, toolkit.menuShortcutKeyMaskEx or KeyEvent.SHIFT_DOWN_MASK),
            "Format"
        )

        searchTextField.inputMap.put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            "Esc"
        )

        searchTextField.actionMap.put("Esc", object : AbstractAction("Esc") {
            override fun actionPerformed(e: ActionEvent) {
                textArea.clearMarkAllHighlights()
                textArea.requestFocusInWindow()
                findPanel.isVisible = false
            }
        })

        closeFindPanelBtn.addActionListener { searchTextField.actionMap.get("Esc").actionPerformed(it) }

        textArea.actionMap.put("Save", object : AbstractAction("Save") {
            override fun actionPerformed(e: ActionEvent) {
                file.writeText(textArea.text, Charsets.UTF_8)
                text = textArea.text
                window.title = file.name
            }
        })

        textArea.actionMap.put("Format", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (textArea.syntaxEditingStyle == SyntaxConstants.SYNTAX_STYLE_JSON) {
                    runCatching {
                        val json = prettyJson.parseToJsonElement(textArea.text)
                        textArea.text = prettyJson.encodeToString(json)
                    }.onFailure {
                        if (log.isErrorEnabled) {
                            log.error(it.message, it)
                        }
                    }
                } else if (textArea.syntaxEditingStyle == SyntaxConstants.SYNTAX_STYLE_XML) {
                    runCatching {
                        val document = SAXReader().read(StringReader(textArea.text))
                        val sw = StringWriter()
                        val writer = XMLWriter(sw, OutputFormat.createPrettyPrint())
                        writer.write(document)
                        textArea.text = sw.toString()
                    }.onFailure {
                        if (log.isErrorEnabled) {
                            log.error(it.message, it)
                        }
                    }
                }
            }
        })

        textArea.actionMap.put("Find", object : AbstractAction("Find") {
            override fun actionPerformed(e: ActionEvent) {
                findPanel.isVisible = true
                searchTextField.selectAll()
                searchTextField.requestFocusInWindow()
            }
        })

        textArea.document.addDocumentListener(object : DocumentAdaptor() {
            override fun changedUpdate(e: DocumentEvent) {
                window.title = if (textArea.text.hashCode() != text.hashCode()) {
                    "${file.name} *"
                } else {
                    file.name
                }
            }
        })

        searchTextField.document.addDocumentListener(object : DocumentAdaptor() {
            override fun changedUpdate(e: DocumentEvent) {
                search()
            }
        })

        searchTextField.addActionListener { nextBtn.doClick(0) }



        prettyBtn.addActionListener(searchTextField.actionMap.get("Format"))

        prevBtn.addActionListener { search(false) }
        nextBtn.addActionListener { search(true) }
    }

    private fun search(searchForward: Boolean = true) {
        textArea.clearMarkAllHighlights()


        val text: String = searchTextField.getText()
        if (text.isEmpty()) return
        context.searchFor = text
        context.searchForward = searchForward
        context.wholeWord = false
        val result = SearchEngine.find(textArea, context)

        prevBtn.isEnabled = result.markedCount > 0
        nextBtn.isEnabled = result.markedCount > 0

    }

    fun changes() = text != textArea.text

    private inner class LayeredPane : JLayeredPane() {
        override fun doLayout() {
            synchronized(treeLock) {
                for (c in components) {
                    if (c == findPanel) {
                        val height = max(findPanel.preferredSize.height, findPanel.height)
                        val x = width / 2
                        c.setBounds(x, 1, width - x, height)
                    } else {
                        c.setBounds(0, 0, width, height)
                    }
                }
            }
        }
    }
}