/*
 * Copyright (C) 2026 Robert Scott
 *
 * This file is part of LeverFrame.
 *
 * LeverFrame is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LeverFrame is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LeverFrame.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.edranor.leverframe

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
