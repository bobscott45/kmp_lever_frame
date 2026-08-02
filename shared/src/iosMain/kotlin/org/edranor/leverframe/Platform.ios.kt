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
/**
 * iOS implementation of platform-specific utilities.
 * Handles device discovery info and idle timer (screen awake) locks.
 */
package org.edranor.leverframe
import org.edranor.leverframe.*

import org.edranor.leverframe.network.*
import org.edranor.leverframe.services.*
import org.edranor.leverframe.ui.screens.main.*
import org.edranor.leverframe.ui.components.*
import org.edranor.leverframe.ui.theme.*
import org.edranor.leverframe.di.*
import org.edranor.leverframe.ui.screens.editor.*
import org.edranor.leverframe.domain.models.*
import org.edranor.leverframe.config.*
import org.edranor.leverframe.ui.screens.schematic.*
import org.edranor.leverframe.domain.engine.*
import org.edranor.leverframe.domain.parser.*

import platform.UIKit.UIDevice
import platform.UIKit.UIApplication
import platform.Foundation.NSUserDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * iOS implementation of [Platform]. Provides platform information specific to Apple devices.
 * 
 * In this architecture, it satisfies the expect declaration for iOS targets, supplying
 * device and system version data to the common codebase.
 */
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
    NSUserDefaults.standardUserDefaults.setObject(json, forKey = "leverframe_config")
}

actual suspend fun loadConfigFromFile(): String? {
    return NSUserDefaults.standardUserDefaults.stringForKey("leverframe_config")
}

actual suspend fun saveLeverStatesToFile(json: String) {
    NSUserDefaults.standardUserDefaults.setObject(json, forKey = "leverframe_states")
}

actual suspend fun loadLeverStatesFromFile(): String? {
    return NSUserDefaults.standardUserDefaults.stringForKey("leverframe_states")
}

actual suspend fun clearLeverStatesFile() {
    NSUserDefaults.standardUserDefaults.removeObjectForKey("leverframe_states")
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

actual val isAppExitAvailable: Boolean = false
actual fun exitApp() {}

actual val isSystemPowerControlAvailable: Boolean = false
actual fun shutdownSystem() {}