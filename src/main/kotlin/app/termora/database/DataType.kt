package app.termora.database

enum class DataType : IDataType {
    Host,
    Snippet,
    KeyPair,
    Tag,
    Macro,
    KeywordHighlight,
    Keymap, ;

    override fun dataType(): String {
        return this.name
    }
}
