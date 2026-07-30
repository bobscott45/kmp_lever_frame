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
 * Defines expect/actual capabilities for platform-specific utilities like networking
 * discovery, keeping the screen awake, and persistence file I/O operations.
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

/**
 * Represents the platform on which the application is running, providing a unified interface
 * to access platform-specific details.
 * 
 * In this architecture, [Platform] acts as an abstraction layer to decouple cross-platform
 * business logic from underlying OS idiosyncrasies, such as OS names or SDK versions.
 */
interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun getLocalIpAddress(): String

@androidx.compose.runtime.Composable
expect fun KeepScreenOn(keepOn: Boolean = true)

expect suspend fun saveConfigToFile(json: String)

expect suspend fun loadConfigFromFile(): String?

expect suspend fun saveLeverStatesToFile(json: String)

expect suspend fun loadLeverStatesFromFile(): String?

expect suspend fun clearLeverStatesFile()