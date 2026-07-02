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

@Composable
actual fun KeepScreenOn(keepOn: Boolean) {
    DisposableEffect(keepOn) {
        UIApplication.sharedApplication.idleTimerDisabled = keepOn
        onDispose {
            UIApplication.sharedApplication.idleTimerDisabled = false
        }
    }
}