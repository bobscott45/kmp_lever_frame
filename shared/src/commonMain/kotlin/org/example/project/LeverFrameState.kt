package org.example.project

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

sealed interface LeverFrameIntent {
    data class TabSelected(val index: Int) : LeverFrameIntent
    data class ToggleLever(val tabIndex: Int, val leverIndex: Int) : LeverFrameIntent
    data class ToggleManualLock(val tabIndex: Int, val leverIndex: Int) : LeverFrameIntent
    data class LeverLabelClicked(val leverIndex: Int) : LeverFrameIntent
    data object EnterConfigMode : LeverFrameIntent
    data object ExitConfigMode : LeverFrameIntent
    data object ConfigSaved : LeverFrameIntent
    data object EnterStatusMode : LeverFrameIntent
    data object ExitStatusMode : LeverFrameIntent
    data object DismissStatusLever : LeverFrameIntent
    data object DismissNetworkError : LeverFrameIntent
    data class SetLeverLccEnabled(val tabIndex: Int, val leverIndex: Int, val enabled: Boolean) : LeverFrameIntent
    data class UpdateSystemConfig(val newConfig: JsonConfig) : LeverFrameIntent
}
