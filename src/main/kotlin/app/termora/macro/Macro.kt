package app.termora.macro

import app.termora.AES.decodeBase64
import app.termora.toSimpleString
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Macro(
    val id: String = UUID.randomUUID().toSimpleString(),
    val macro: String = String(),
    val name: String = String(),
    /**
     * 越小越靠前
     */
    val created: Long = System.currentTimeMillis(),

    /**
     * 越大越靠前
     */
    val sort: Long = System.currentTimeMillis(),

    /**
     * 更新时间
     */
    val updateDate: Long = System.currentTimeMillis(),
) {
    val macroByteArray by lazy { macro.decodeBase64() }
}