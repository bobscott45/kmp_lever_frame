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


