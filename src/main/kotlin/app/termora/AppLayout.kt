package app.termora

enum class AppLayout {
    /**
     * Windows
     */
    Zip,
    Exe,
    Appx,

    /**
     * macOS
     */
    App,
    AppStore,

    /**
     * Linux
     */
    TarGz,
    AppImage,
    Deb,
}