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
