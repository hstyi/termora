package app.termora.snippet

import app.termora.toSimpleString
import kotlinx.serialization.Serializable
import org.apache.commons.lang3.StringUtils
import java.util.*

enum class SnippetType {
    Folder,
    Snippet,
}

@Serializable
data class Snippet(
    val id: String = UUID.randomUUID().toSimpleString(),
    val name: String,
    val snippet: String = StringUtils.EMPTY,
    val parentId: String = StringUtils.EMPTY,
    val type: SnippetType = SnippetType.Snippet,
    val deleted: Boolean = false,
    val sort: Long = System.currentTimeMillis(),
    val createDate: Long = System.currentTimeMillis(),
    val updateDate: Long = System.currentTimeMillis(),
)