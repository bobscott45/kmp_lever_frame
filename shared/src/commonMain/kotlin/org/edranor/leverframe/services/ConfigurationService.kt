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
 * Core business service responsible for loading, parsing, updating, and saving
 * the JSON configuration model representing the lever frames.
 */
package org.edranor.leverframe.services
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages reading and writing application configurations to persistence,
 * and exposes the current configuration state to the rest of the application.
 * 
 * @property configRepo The underlying repository handling raw storage and retrieval.
 */
class ConfigurationService(
    private val configRepo: ConfigurationRepository
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _configState = MutableStateFlow(ConfigState())
    val configState: StateFlow<ConfigState> = _configState.asStateFlow()

    /**
     * Initializes the service by delegating to the repository and loading the
     * parsed configuration into memory.
     */
    suspend fun initialize() {
        configRepo.initConfig()
        loadConfig()
    }

    /**
     * Loads the raw JSON string from the repository, parses it into domain models,
     * and updates the exposed [ConfigState].
     */
    suspend fun loadConfig() {
        val configStr = configRepo.toJsonString()
        val parsedTabs = configRepo.parseConfig(configStr)
        val initialVersion = _configState.value.configVersion
        
        _configState.update {
            it.copy(
                tabs = parsedTabs,
                configVersion = initialVersion + 1,
                config = configRepo.currentConfig
            )
        }
    }

    /**
     * Toggles whether a specific lever should participate in LCC network events.
     *
     * @param tabIndex The index of the frame containing the lever.
     * @param leverIndex The index of the lever within the frame.
     * @param enabled True to enable LCC communication, false to disable.
     */
    fun setLeverLccEnabled(tabIndex: Int, leverIndex: Int, enabled: Boolean) {
        val newTabsJson = configRepo.currentConfig.tabs.toMutableList()
        val currentTabJson = newTabsJson[tabIndex].copy()
        val newLeversJson = currentTabJson.levers.toMutableList()
        newLeversJson[leverIndex] = newLeversJson[leverIndex].copy(lcc_enabled = enabled)
        val newConfig = configRepo.currentConfig.copy(
            tabs = newTabsJson.apply { set(tabIndex, currentTabJson.copy(levers = newLeversJson)) }
        )
        coroutineScope.launch {
            configRepo.saveConfig(newConfig)
            loadConfig()
        }
    }

    /**
     * Updates the persistent display layout weighting for different orientations.
     *
     * @param landscapeWeight The weight given to the schematic side when in landscape.
     * @param portraitWeight The weight given to the schematic side when in portrait.
     */
    fun saveLayoutWeights(landscapeWeight: Float, portraitWeight: Float) {
        val newConfig = configRepo.currentConfig.copy(
            schematic_weight_landscape = landscapeWeight,
            schematic_weight_portrait = portraitWeight
        )
        coroutineScope.launch {
            configRepo.saveConfig(newConfig)
            _configState.update { it.copy(config = newConfig) }
        }
    }

    /**
     * Applies a new complete configuration to the system.
     *
     * @param newConfig The updated system configuration.
     * @param rulesOnly If true, bypasses rebuilding the full domain state to quickly update interlocking logic.
     */
    suspend fun updateSystemConfig(newConfig: JsonConfig, rulesOnly: Boolean = false) {
        configRepo.saveConfig(newConfig)
        if (rulesOnly) {
            val configStr = configRepo.toJsonString()
            val parsedTabs = configRepo.parseConfig(configStr)
            _configState.update { 
                it.copy(
                    tabs = parsedTabs,
                    config = configRepo.currentConfig
                )
            }
        } else {
            loadConfig()
        }
    }
}
