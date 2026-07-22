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

import platform.UIKit.UIDevice
import platform.UIKit.UIApplication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getLocalIpAddress(): String {
    // For iOS, returning a placeholder as fetching IP requires POSIX headers which 
    // are tricky to expose cleanly without complex cinterop or specific network frameworks.
    return "Unknown (iOS)"
}

actual suspend fun saveConfigToFile(json: String) {
    // Placeholder for iOS
}

actual suspend fun loadConfigFromFile(): String? {
    // Placeholder for iOS
    return null
}

actual suspend fun saveLeverStatesToFile(json: String) {
    // Placeholder
}

actual suspend fun loadLeverStatesFromFile(): String? {
    return null
}

actual suspend fun clearLeverStatesFile() {
    // Placeholder
}

@Composable
actual fun KeepScreenOn(keepOn: Boolean) {
    DisposableEffect(keepOn) {
        UIApplication.sharedApplication.idleTimerDisabled = keepOn
        onDispose {
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }
}