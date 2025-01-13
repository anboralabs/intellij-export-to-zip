package co.anbora.labs.export.project.zip.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object ExportIcons {

    val FILE = getIcon("file.svg")

    private fun getIcon(path: String): Icon {
        return IconLoader.findIcon("/icons/$path", ExportIcons::class.java.classLoader) as Icon
    }
}