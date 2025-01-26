package app.termora.terminal

import org.slf4j.LoggerFactory

class DeviceControlStringProcessor(terminal: Terminal, reader: TerminalReader) : AbstractProcessor(terminal, reader) {

    companion object {
        private val log = LoggerFactory.getLogger(DeviceControlStringProcessor::class.java)
    }

    private val systemCommandSequence = SystemCommandSequence()

    override fun process(ch: Char): ProcessorState {
        // 回退回去，然后重新读取出来
        reader.addFirst(ch)

        do {

            if (systemCommandSequence.process(reader.read())) {
                break
            }

            // 如果没有检测到结束，那么退出重新来
            if (reader.isEmpty()) {
                return TerminalState.DCS
            }

        } while (reader.isNotEmpty())

        processCommand(systemCommandSequence.getCommand())

        systemCommandSequence.reset()

        return TerminalState.READY
    }


    private fun processCommand(command: String) {
        if (command.isEmpty()) {
            return
        }
        if (log.isWarnEnabled) {
            log.warn("Cannot process command: {}", command)
        }
    }
}