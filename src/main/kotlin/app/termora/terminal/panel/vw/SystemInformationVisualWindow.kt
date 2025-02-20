package app.termora.terminal.panel.vw

import app.termora.*
import app.termora.actions.AnActionEvent
import app.termora.actions.DataProviders
import com.jgoodies.forms.builder.FormBuilder
import com.jgoodies.forms.layout.FormLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.apache.commons.lang3.StringUtils
import org.apache.sshd.client.session.ClientSession
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import kotlin.time.Duration.Companion.milliseconds


class SystemInformationVisualWindow(private val tab: SSHTerminalTab, visualWindowManager: VisualWindowManager) :
    VisualWindowPanel("SystemInformation", visualWindowManager) {

    companion object {
        private val log = LoggerFactory.getLogger(SystemInformationVisualWindow::class.java)
    }

    private val systemInformationPanel by lazy { SystemInformationPanel() }

    init {
        initViews()
        initEvents()
        initVisualWindowPanel()
    }


    private fun initViews() {
        title = I18n.getString("termora.visual-window.system-information")
        add(systemInformationPanel, BorderLayout.CENTER)
    }

    private fun initEvents() {
        Disposer.register(this, systemInformationPanel)
        Disposer.register(tab, this)
    }

    override fun getWindowTitle(): String {
        return tab.getTitle() + " - " + title
    }

    override fun toggleWindow() {
        val evt = AnActionEvent(tab.getJComponent(), StringUtils.EMPTY, EventObject(this))
        val terminalTabbedManager = evt.getData(DataProviders.TerminalTabbedManager) ?: return

        super.toggleWindow()

        if (!isWindow()) {
            terminalTabbedManager.setSelectedTerminalTab(tab)
        }
    }

    private inner class SystemInformationPanel : JPanel(BorderLayout()), Disposable {

        private val coroutineScope = CoroutineScope(Dispatchers.IO)

        private val cpuProgressBar = SmartProgressBar()
        private val memoryProgressBar = SmartProgressBar()
        private val swapProgressBar = SmartProgressBar()
        private val mem = Mem()
        private val cpu = CPU()
        private val swap = Swap()
        private val tableModel = object : DefaultTableModel() {
            override fun isCellEditable(row: Int, column: Int): Boolean {
                return false
            }
        }

        init {
            initViews()
            initEvents()
        }


        private fun initViews() {
            add(createPanel(), BorderLayout.CENTER)
        }

        private fun createPanel(): JComponent {
            val formMargin = "4dlu"
            var rows = 1
            val step = 2
            val p = JPanel(BorderLayout())
            val n = FormBuilder.create().debug(false).layout(
                FormLayout(
                    "left:pref, $formMargin, default:grow",
                    "pref, $formMargin, pref, $formMargin, pref, $formMargin"
                )
            )
                .add("CPU: ").xy(1, rows)
                .add(cpuProgressBar).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.visual-window.system-information.mem")}: ").xy(1, rows)
                .add(memoryProgressBar).xy(3, rows).apply { rows += step }
                .add("${I18n.getString("termora.visual-window.system-information.swap")}: ").xy(1, rows)
                .add(swapProgressBar).xy(3, rows).apply { rows += step }
                .build()

            val table = JTable(tableModel)
            table.tableHeader.isEnabled = false
            table.showVerticalLines = true
            table.showHorizontalLines = true
            table.fillsViewportHeight = true

            tableModel.addColumn(I18n.getString("termora.visual-window.system-information.filesystem"))
            tableModel.addColumn(I18n.getString("termora.visual-window.system-information.used-total"))

            val centerRenderer = DefaultTableCellRenderer()
            centerRenderer.setHorizontalAlignment(JLabel.CENTER)
            table.columnModel.getColumn(1).cellRenderer = centerRenderer


            p.add(n, BorderLayout.NORTH)
            p.add(JScrollPane(table).apply {
                border = BorderFactory.createMatteBorder(1, 1, 1, 1, DynamicColor.BorderColor)
            }, BorderLayout.CENTER)
            p.border = BorderFactory.createEmptyBorder(6, 6, 6, 6)

            return p
        }

        private fun initEvents() {
            coroutineScope.launch {
                while (coroutineScope.isActive) {
                    try {
                        refresh()
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    } finally {
                        delay(1000.milliseconds)
                    }
                }
            }
        }

        private suspend fun refresh() {
            val session = tab.getData(SSHTerminalTab.SSHSession) ?: return

            try {
                // 刷新 CPU 和 内存
                refreshCPUAndMem(session)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error("refreshCPUAndMem", e)
                }
            }

            try {
                // 刷新磁盘
                refreshDisk(session)
            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error("refreshDisk", e)
                }
            }

        }

        private suspend fun refreshCPUAndMem(session: ClientSession) {

            // top
            var pair = SshClients.execChannel(session, "top -bn1")
            if (pair.first != 0) {
                return
            }

            val lines = pair.second.split(StringUtils.LF)
            for (line in lines) {
                val isCPU = line.startsWith("%Cpu(s):", true)
                val isMibMem = line.startsWith("MiB Mem :", true)
                val isKibMem = line.startsWith("KiB Mem :", true)
                val isMibSwap = line.startsWith("MiB Swap:", true)
                val isKibSwap = line.startsWith("KiB Swap:", true)
                val unit = if (isKibSwap || isKibMem) 'K' else 'M'

                if (isCPU) {
                    val parts = StringUtils.removeStartIgnoreCase(line, "%Cpu(s):").split(",").map { it.trim() }
                    for (part in parts) {
                        if (part.endsWith("us")) {
                            cpu.us = StringUtils.removeEnd(part, "us").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.endsWith("sy")) {
                            cpu.sy = StringUtils.removeEnd(part, "sy").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.endsWith("ni")) {
                            cpu.ni = StringUtils.removeEnd(part, "ni").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.endsWith("id")) {
                            cpu.id = StringUtils.removeEnd(part, "id").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.endsWith("wa")) {
                            cpu.wa = StringUtils.removeEnd(part, "wa").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.endsWith("hi")) {
                            cpu.hi = StringUtils.removeEnd(part, "hi").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.endsWith("si")) {
                            cpu.si = StringUtils.removeEnd(part, "si").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.endsWith("st")) {
                            cpu.st = StringUtils.removeEnd(part, "st").trim().toDoubleOrNull() ?: 0.0
                        }
                    }
                } else if (isMibMem || isKibMem) {
                    val parts = StringUtils.removeStartIgnoreCase(line, "${unit}iB Mem :")
                        .split(",")
                        .map { it.trim() }
                    for (part in parts) {
                        if (part.endsWith("total")) {
                            mem.total = StringUtils.removeEnd(part, "total").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.endsWith("free")) {
                            mem.free = StringUtils.removeEnd(part, "free").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.endsWith("used")) {
                            mem.used = StringUtils.removeEnd(part, "used").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.endsWith("buff/cache")) {
                            mem.buffCache = StringUtils.removeEnd(part, "buff/cache").trim().toDoubleOrNull() ?: 0.0
                        }
                    }

                    if (isKibMem) {
                        mem.total = mem.total / 1024.0
                        mem.free = mem.free / 1024.0
                        mem.used = mem.used / 1024.0
                        mem.buffCache = mem.buffCache / 1024.0
                    }
                } else if (isMibSwap || isKibSwap) {
                    val parts = StringUtils.removeStartIgnoreCase(line, "${unit}iB Swap:")
                        .split(",")
                        .map { it.trim() }

                    for (part in parts) {
                        if (part.contains("total")) {
                            swap.total = StringUtils.removeEnd(part, "total").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.contains("free")) {
                            swap.free = StringUtils.removeEnd(part, "free").trim().toDoubleOrNull() ?: 0.0
                        } else if (part.contains("used.")) {
                            swap.used = part.substringBefore("used.").trim().toDoubleOrNull() ?: 0.0
                        }
                    }

                    if (isKibSwap) {
                        swap.total = swap.total / 1024.0
                        swap.free = swap.free / 1024.0
                        swap.used = swap.used / 1024.0
                    }
                }
            }

            withContext(Dispatchers.Swing) {
                cpuProgressBar.value = (100.0 - cpu.id).toInt()
                memoryProgressBar.value = (mem.used / mem.total * 100.0).toInt()
                memoryProgressBar.string =
                    "${formatBytes((mem.used * 1024 * 1024).toLong())} / ${formatBytes((mem.total * 1024 * 1024).toLong())}"

                swapProgressBar.value = (swap.used / swap.total * 100.0).toInt()
                swapProgressBar.string =
                    "${formatBytes((swap.used * 1024 * 1024).toLong())} / ${formatBytes((swap.total * 1024 * 1024).toLong())}"
            }
        }

        private suspend fun refreshDisk(session: ClientSession) {

            // df -h
            var pair = SshClients.execChannel(session, "df -B1")
            if (pair.first != 0) {
                return
            }

            val disks = mutableListOf<Disk>()
            val lines = pair.second.split(StringUtils.LF)
            for (line in lines) {
                if (!line.startsWith("/dev/")) {
                    continue
                }

                val parts = line.split("\\s+".toRegex())
                if (parts.size < 6) {
                    continue
                }

                disks.add(
                    Disk(
                        filesystem = parts[0],
                        size = parts[1].toLong(),
                        used = parts[2].toLong(),
                        avail = parts[3].toLong(),
                        usePercentage = StringUtils.removeEnd(parts[4], "%").toIntOrNull() ?: 0,
                        mountedOn = parts[5],
                    )
                )
            }

            withContext(Dispatchers.Swing) {
                while (tableModel.rowCount > 0) {
                    tableModel.removeRow(0)
                }

                for (disk in disks) {
                    tableModel.addRow(
                        arrayOf(
                            " ${disk.filesystem}",
                            formatBytes(disk.used) + " / " + formatBytes(disk.size),
                        )
                    )
                }
            }
        }

        override fun dispose() {
            coroutineScope.cancel()
        }

    }

    private data class Mem(
        /**
         * 总内存
         */
        var total: Double = 0.0,
        /**
         * 空闲内存
         */
        var free: Double = 0.0,
        /**
         * 已用内存
         */
        var used: Double = 0.0,
        /**
         * 缓存和缓冲区占用的内存
         */
        var buffCache: Double = 0.0,
    )

    private data class Swap(
        /**
         * 交换空间的总大小
         */
        var total: Double = 0.0,
        /**
         * 已使用的交换空间
         */
        var free: Double = 0.0,
        /**
         * 未使用的交换空间
         */
        var used: Double = 0.0,
    )

    private data class CPU(
        /**
         * 用户空间 CPU 占用时间百分比。
         * 该值表示 CPU 用于执行用户进程的时间比例。
         * 示例：如果系统中 CPU 用于执行用户程序的时间占总 CPU 时间的 40%，则该值为 40.0。
         */
        var us: Double = 0.0,

        /**
         * 系统空间 CPU 占用时间百分比。
         * 该值表示 CPU 用于执行内核进程的时间比例。
         * 示例：如果内核进程占用 CPU 时间的 20%，则该值为 20.0。
         */
        var sy: Double = 0.0,

        /**
         * 优先级调整过的进程（Nice）占用的 CPU 时间百分比。
         * 该值表示 CPU 用于执行“优先级较低的”进程的时间比例。
         * 示例：如果优先级调整过的进程占用 CPU 时间的 10%，则该值为 10.0。
         */
        var ni: Double = 0.0,

        /**
         * CPU 空闲时间百分比。
         * 该值表示 CPU 在空闲状态下没有执行任何任务的时间比例。
         * 示例：如果 CPU 95% 处于空闲状态，该值为 95.0。
         */
        var id: Double = 0.0,

        /**
         * I/O 等待时间百分比。
         * 该值表示 CPU 正在等待 I/O 操作完成的时间比例。
         * 示例：如果 CPU 由于 I/O 操作等待占用 5% 的时间，则该值为 5.0。
         */
        var wa: Double = 0.0,

        /**
         * 硬件中断处理时间百分比。
         * 该值表示 CPU 用于处理中断请求的时间比例，通常由硬件触发。
         * 示例：如果 CPU 处理硬件中断占用 2% 的时间，则该值为 2.0。
         */
        var hi: Double = 0.0,

        /**
         * 软件中断处理时间百分比。
         * 该值表示 CPU 用于处理由软件触发的中断的时间比例。
         * 示例：如果 CPU 处理软件中断占用 3% 的时间，则该值为 3.0。
         */
        var si: Double = 0.0,

        /**
         * 虚拟化环境中的 CPU 抢占时间百分比。
         * 该值表示 CPU 在虚拟化环境中被其他虚拟机抢占的时间比例。
         * 示例：如果虚拟化环境中的 CPU 抢占占用 0.5% 的时间，则该值为 0.5。
         */
        var st: Double = 0.0,
    )

    private data class Disk(
        var filesystem: String = StringUtils.EMPTY,
        /**
         *  总大小
         */
        var size: Long = 0L,
        /**
         *  已经使用的空间
         */
        var used: Long = 0L,
        /**
         *  可用空间
         */
        var avail: Long = 0L,
        /**
         *  已经使用的百分比
         */
        var usePercentage: Int = 0,

        /**
         * 挂载点
         */
        var mountedOn: String = StringUtils.EMPTY
    )

}