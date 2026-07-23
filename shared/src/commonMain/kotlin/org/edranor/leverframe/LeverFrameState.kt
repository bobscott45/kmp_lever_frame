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

enum class ConfigMode { NONE, SYSTEM, FRAMES }

data class DomainState(
    val frames: List<DomainFrame> = emptyList(),
    val conflictingLevers: List<Int> = emptyList()
)

data class DomainFrame(
    val id: Int,
    val levers: List<DomainLever>,
    val blocks: List<DomainBlock>
)

data class DomainLever(
    val id: Int,
    val isReversed: Boolean,
    val isManuallyLocked: Boolean = false
)

data class DomainBlock(
    val id: Int,
    val isOccupied: Boolean
)

data class ConfigState(
    val tabs: List<Pair<String, TabDef>> = emptyList(),
    val configVersion: Int = 0,
    val config: JsonConfig = JsonConfig()
)

data class TransientUiState(
    val selectedTabIndex: Int = 0,
    val configMode: ConfigMode = ConfigMode.NONE,
    val isStatusMode: Boolean = false,
    val statusLeverIndex: Int? = null,
    val initialEditFrameIndex: Int? = null,
    val initialEditLeverIndex: Int? = null,
    val errorMessage: String? = null,
    val networkError: String? = null,
    val networkStatus: String = "Disconnected"
)
