package app.termora.protocol

import app.termora.Host
import org.apache.commons.lang3.StringUtils
import java.awt.Window

class FileObjectRequester(
    val host: Host,
    val owner: Window? = null,
)