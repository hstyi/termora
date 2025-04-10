package app.termora

import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities

class BackgroundManager private constructor() {
    companion object {
        private val log = LoggerFactory.getLogger(BackgroundManager::class.java)
        fun getInstance(): BackgroundManager {
            return ApplicationScope.forApplicationScope().getOrCreate(BackgroundManager::class) { BackgroundManager() }
        }
    }

    private val appearance get() = Database.getDatabase().appearance
    private var bufferedImage: BufferedImage? = null
    private var imageFilepath = StringUtils.EMPTY

    fun setBackgroundImage(file: File) {
        synchronized(this) {
            try {
                bufferedImage = file.inputStream().use { ImageIO.read(it) }
                imageFilepath = file.absolutePath
                appearance.backgroundImage = file.absolutePath

                SwingUtilities.invokeLater {
                    for (window in TermoraFrameManager.getInstance().getWindows()) {
                        SwingUtilities.updateComponentTreeUI(window)
                    }
                }

            } catch (e: Exception) {
                if (log.isErrorEnabled) {
                    log.error(e.message, e)
                }
            }
        }
    }

    fun getBackgroundImage(): BufferedImage? {
        val bg = doGetBackgroundImage()
        if (bg == null) {
            if (JPopupMenu.getDefaultLightWeightPopupEnabled()) {
                return null
            } else {
                JPopupMenu.setDefaultLightWeightPopupEnabled(true)
            }
        } else {
            if (JPopupMenu.getDefaultLightWeightPopupEnabled()) {
                JPopupMenu.setDefaultLightWeightPopupEnabled(false)
            }
        }
        return bg
    }

    private fun doGetBackgroundImage(): BufferedImage? {
        synchronized(this) {
            if (bufferedImage == null || imageFilepath.isEmpty()) {
                if (appearance.backgroundImage.isBlank()) {
                    return null
                }
                val file = File(appearance.backgroundImage)
                if (file.exists()) {
                    setBackgroundImage(file)
                }
            }

            return bufferedImage
        }
    }

    fun clearBackgroundImage() {
        synchronized(this) {
            bufferedImage = null
            imageFilepath = StringUtils.EMPTY
            appearance.backgroundImage = StringUtils.EMPTY
            SwingUtilities.invokeLater {
                for (window in TermoraFrameManager.getInstance().getWindows()) {
                    SwingUtilities.updateComponentTreeUI(window)
                }
            }
        }
    }
}