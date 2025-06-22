package app.termora.transfer

import app.termora.Disposable
import app.termora.Disposer
import app.termora.DynamicColor
import app.termora.actions.DataProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.nio.file.Path
import javax.swing.*


class TransportViewer : JPanel(BorderLayout()), DataProvider, Disposable {
    companion object {
        private val log = LoggerFactory.getLogger(TransportViewer::class.java)
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val splitPane = JSplitPane()
    private val transferManager = TransferTableModel(coroutineScope)
    private val transferTable = TransferTable(coroutineScope, transferManager)
    private val leftTabbed = TransportTabbed(transferManager)
    private val rightTabbed = TransportTabbed(transferManager)
    private val leftTransferManager = createInternalTransferManager(leftTabbed, rightTabbed)
    private val rightTransferManager = createInternalTransferManager(rightTabbed, leftTabbed)
    private val rootSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
    private val owner get() = SwingUtilities.getWindowAncestor(this)

    init {
        initView()
        initEvents()
    }

    private fun initView() {
        isFocusable = false

        leftTabbed.internalTransferManager = leftTransferManager
        rightTabbed.internalTransferManager = rightTransferManager

        leftTabbed.addLocalTab()
        rightTabbed.addSelectionTab()

        val scrollPane = JScrollPane(transferTable)
        scrollPane.border = BorderFactory.createMatteBorder(1, 0, 0, 0, DynamicColor.BorderColor)

        leftTabbed.border = BorderFactory.createMatteBorder(0, 0, 0, 1, DynamicColor.BorderColor)
        rightTabbed.border = BorderFactory.createMatteBorder(0, 1, 0, 0, DynamicColor.BorderColor)

        splitPane.resizeWeight = 0.5
        splitPane.leftComponent = leftTabbed
        splitPane.rightComponent = rightTabbed
        splitPane.border = BorderFactory.createMatteBorder(0, 0, 1, 0, DynamicColor.BorderColor)

        rootSplitPane.resizeWeight = 0.7
        rootSplitPane.topComponent = splitPane
        rootSplitPane.bottomComponent = scrollPane

        add(rootSplitPane, BorderLayout.CENTER)
    }

    private fun initEvents() {
        splitPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                removeComponentListener(this)
                splitPane.setDividerLocation(splitPane.resizeWeight)
            }
        })

        rootSplitPane.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                removeComponentListener(this)
                rootSplitPane.setDividerLocation(rootSplitPane.resizeWeight)
            }
        })

        Disposer.register(this, leftTabbed)
        Disposer.register(this, rightTabbed)
    }

    private fun createInternalTransferManager(
        source: TransportTabbed,
        target: TransportTabbed
    ): InternalTransferManager {
        return DefaultInternalTransferManager(
            { owner },
            coroutineScope,
            transferManager,
            object : DefaultInternalTransferManager.WorkdirProvider {
                override fun getWorkdir(): Path? {
                    return source.getSelectedTransportPanel()?.workdir
                }

                override fun getTableModel(): TransportTableModel? {
                    return source.getSelectedTransportPanel()?.getTableModel()
                }

            },
            object : DefaultInternalTransferManager.WorkdirProvider {
                override fun getWorkdir(): Path? {
                    return target.getSelectedTransportPanel()?.workdir
                }

                override fun getTableModel(): TransportTableModel? {
                    return target.getSelectedTransportPanel()?.getTableModel()
                }
            })

    }

    fun getTransferManager(): TransferManager {
        return transferManager
    }

    fun getLeftTabbed(): TransportTabbed {
        return leftTabbed
    }

    fun getRightTabbed(): TransportTabbed {
        return rightTabbed
    }

}