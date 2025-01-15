package app.termora

import app.termora.actions.DataProvider
import app.termora.actions.DataProviderSupport
import app.termora.actions.DataProviders
import app.termora.terminal.DataKey
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

class TerminalTabDialog(
    owner: Window,
    size: Dimension,
    private val terminalTab: TerminalTab
) : DialogWrapper(null), Disposable, DataProvider {

    private val dataProviderSupport = DataProviderSupport()

    init {
        title = terminalTab.getTitle()
        isModal = false
        isAlwaysOnTop = false
        iconImages = owner.iconImages
        escapeDispose = false
        processGlobalKeymap = true

        super.setSize(size)

        init()

        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                if (terminalTab.canClose()) {
                    SwingUtilities.invokeLater { doCancelAction() }
                }
            }
        })

        setLocationRelativeTo(null)


        if (owner is DataProvider) {
            owner.getData(DataProviders.WindowScope)?.let {
                dataProviderSupport.addData(DataProviders.WindowScope, it)
            }
        }

        dataProviderSupport.addData(DataProviders.TerminalTab, terminalTab)
    }

    override fun createSouthPanel(): JComponent? {
        return null
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(terminalTab.getJComponent(), BorderLayout.CENTER)
        panel.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)
        return panel
    }

    override fun dispose() {
        Disposer.dispose(this)
        super<DialogWrapper>.dispose()
    }

    override fun <T : Any> getData(dataKey: DataKey<T>): T? {
        return dataProviderSupport.getData(dataKey)
    }

}