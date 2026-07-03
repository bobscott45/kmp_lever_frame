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

data class LeverFrameUiState(
    val tabs: List<Pair<String, TabDef>> = emptyList(),
    val selectedTabIndex: Int = 0,
    val leverStates: List<BooleanArray> = emptyList(),
    val manualLocks: List<BooleanArray> = emptyList(),
    val isConfigMode: Boolean = false,
    val isStatusMode: Boolean = false,
    val statusLeverIndex: Int? = null,
    val errorMessage: String? = null,
    val networkError: String? = null,
    val conflictingLevers: List<Int> = emptyList(),
    val configVersion: Int = 0,
    val config: JsonConfig = JsonConfig(),
    val networkStatus: String = "Disconnected"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as LeverFrameUiState

        if (tabs != other.tabs) return false
        if (selectedTabIndex != other.selectedTabIndex) return false
        if (isConfigMode != other.isConfigMode) return false
        if (isStatusMode != other.isStatusMode) return false
        if (statusLeverIndex != other.statusLeverIndex) return false
        if (errorMessage != other.errorMessage) return false
        if (networkError != other.networkError) return false
        if (conflictingLevers != other.conflictingLevers) return false
        if (configVersion != other.configVersion) return false
        if (config != other.config) return false
        if (networkStatus != other.networkStatus) return false

        // Custom equality for List<BooleanArray>
        if (leverStates.size != other.leverStates.size) return false
        for (i in leverStates.indices) {
            if (!leverStates[i].contentEquals(other.leverStates[i])) return false
        }

        if (manualLocks.size != other.manualLocks.size) return false
        for (i in manualLocks.indices) {
            if (!manualLocks[i].contentEquals(other.manualLocks[i])) return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = tabs.hashCode()
        result = 31 * result + selectedTabIndex
        result = 31 * result + leverStates.sumOf { it.contentHashCode() }
        result = 31 * result + manualLocks.sumOf { it.contentHashCode() }
        result = 31 * result + isConfigMode.hashCode()
        result = 31 * result + isStatusMode.hashCode()
        result = 31 * result + (statusLeverIndex ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + (networkError?.hashCode() ?: 0)
        result = 31 * result + conflictingLevers.hashCode()
        result = 31 * result + configVersion
        result = 31 * result + config.hashCode()
        result = 31 * result + networkStatus.hashCode()
        return result
    }
}


