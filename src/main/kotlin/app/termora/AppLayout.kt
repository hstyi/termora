package app.termora

enum class AppLayout {
    /**
     * Windows
     */
    Zip,
    Exe,

    /**
     * macOS
     */
    App,

    /**
     * Linux
     */
    TarGz,
    AppImage,
    Deb,
}