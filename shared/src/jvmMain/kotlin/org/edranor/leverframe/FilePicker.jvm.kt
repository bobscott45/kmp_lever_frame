/*
 * Copyright (C) 2026 Robert Scott
 *
 * This file is part of LeverFrame.
 *
 * This project is dual-licensed to balance open-source collaboration with 
 * ecosystem compatibility:
 *
 * * Source Code: The source code in this repository is licensed under the 
 *   GNU General Public License v3 (GPLv3). You are free to copy, modify, 
 *   and self-compile the code, provided any distributions remain open-source 
 *   under the same terms.
 * * Compiled Binaries & Storefronts: As the sole copyright owner of this 
 *   codebase, the author reserves the right to distribute compiled binaries 
 *   (such as on the Apple App Store, Google Play, or other platforms) under 
 *   separate, proprietary, or storefront-specific licenses.
 *
 * Note: If you wish to contribute code to this project via a Pull Request, you 
 * agree to grant the author a non-exclusive, perpetual license to distribute 
 * your contributions under both the GPLv3 and our storefront distribution licenses.
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
