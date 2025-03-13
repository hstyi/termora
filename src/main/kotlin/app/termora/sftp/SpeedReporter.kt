package app.termora.sftp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class SpeedReporter(private val coroutineScope: CoroutineScope) {
    companion object {
        val millis = TimeUnit.MILLISECONDS.toMillis(500)
    }

    private val events = ConcurrentLinkedQueue<Triple<Transport, Long, Long>>()

    init {
        collect()
    }

    fun report(transport: Transport, bytes: Long, time: Long) {
        events.add(Triple(transport, bytes, time))
    }

    private fun collect() {
        // 异步上报数据
        coroutineScope.launch {
            while (coroutineScope.isActive) {
                val time = System.currentTimeMillis()
                val map = linkedMapOf<Transport, Long>()

                // 收集
                while (events.isNotEmpty() && events.peek().second < time) {
                    val (a, b) = events.poll()
                    map[a] = map.computeIfAbsent(a) { 0 } + b
                }

                if (map.isNotEmpty()) {
                    for ((a, b) in map) {
                        if (b > 0) {
                            reportTransferredFilesize(a, b, time)
                        }
                    }
                }

                delay(millis.milliseconds)
            }
        }
    }


    private fun reportTransferredFilesize(transport: Transport, bytes: Long, time: Long) {
        transport.reportTransferredFilesize(bytes, time)
    }
}