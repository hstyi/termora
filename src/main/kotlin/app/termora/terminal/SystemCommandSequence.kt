package app.termora.terminal

class SystemCommandSequence {
    private var isTerminated = false
    private val command = StringBuilder()

    /**
     * @return 返回 true 表示处理完毕
     */
    fun process(c: Char): Boolean {

        if (isTerminated) {
            throw UnsupportedOperationException("Cannot be processed, call the reset method")
        }

        command.append(c)
        if (c == ControlCharacters.BEL || c == ControlCharacters.ST) {
            command.deleteAt(command.lastIndex)
            isTerminated = true
        } else if (c == '\\' && command.length >= 2 && command[command.length - 2] == ControlCharacters.ESC) {
            command.deleteAt(command.lastIndex)
            command.deleteAt(command.lastIndex)
            isTerminated = true
        }

        return isTerminated
    }

    fun getCommand(): String {
        return command.toString()
    }

    fun reset() {
        isTerminated = false
        command.clear()
    }
}