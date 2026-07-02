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

actual val isFilePickerAvailable: Boolean = false

// Stub implementation for Android. 
// A production implementation requires passing the Activity context to an ActivityResultLauncher.
actual fun exportConfigurationFile(json: String): Boolean {
    println("Export not implemented natively for Android yet. Use Clipboard fallback.")
    return false
}

actual fun importConfigurationFile(onResult: (String?) -> Unit) {
    println("Import not implemented natively for Android yet.")
    onResult(null)
}
