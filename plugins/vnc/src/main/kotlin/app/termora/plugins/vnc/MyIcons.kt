package app.termora.plugins.vnc

import app.termora.DynamicIcon

object MyIcons {
    val actualZoom by lazy {
        DynamicIcon(
            "icons/actualZoom.svg",
            "icons/actualZoom_dark.svg",
            loader = VNCPlugin::class.java.classLoader
        )
    }


    val zoomIn by lazy {
        DynamicIcon(
            "icons/zoomIn.svg",
            "icons/zoomIn_dark.svg",
            loader = VNCPlugin::class.java.classLoader
        )
    }

    val zoomOut by lazy {
        DynamicIcon(
            "icons/zoomOut.svg",
            "icons/zoomOut_dark.svg",
            loader = VNCPlugin::class.java.classLoader
        )
    }

    val ctrlAltDel by lazy {
        DynamicIcon(
            "icons/ctrlAltDel.svg",
            "icons/ctrlAltDel_dark.svg",
            loader = VNCPlugin::class.java.classLoader
        )
    }

}