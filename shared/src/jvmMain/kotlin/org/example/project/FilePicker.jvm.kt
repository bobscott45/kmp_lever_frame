package org.example.project

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

actual val isFilePickerAvailable: Boolean = true

actual fun exportConfigurationFile(json: String): Boolean {
    SwingUtilities.invokeLater {
        val dialog = FileDialog(null as Frame?, "Export Configuration", FileDialog.SAVE)
        dialog.file = "config.json"
        dialog.isVisible = true
        if (dialog.directory != null && dialog.file != null) {
            val file = File(dialog.directory, dialog.file)
            file.writeText(json)
        }
    }
    return true
}

actual fun importConfigurationFile(onResult: (String?) -> Unit) {
    SwingUtilities.invokeLater {
        val dialog = FileDialog(null as Frame?, "Import Configuration", FileDialog.LOAD)
        dialog.file = "*.json"
        dialog.isVisible = true
        if (dialog.directory != null && dialog.file != null) {
            val file = File(dialog.directory, dialog.file)
            onResult(file.readText())
        } else {
            onResult(null)
        }
    }
}
