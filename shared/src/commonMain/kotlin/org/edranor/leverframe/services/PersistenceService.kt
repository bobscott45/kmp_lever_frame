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
 * A decoupled background service that observes high-frequency DomainState changes (e.g., lever pulls),
 * debounces them, and writes the resulting state to disk to ensure recovery after power loss or restart.
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Service responsible for automatically saving the lever/block state to disk.
 * Debounces rapid domain state changes to prevent excessive disk writes during cascades.
 */
class PersistenceService(
    private val persistenceRepo: StatePersistenceRepository,
    private val configRepo: ConfigurationRepository,
    private val scope: CoroutineScope,
    private val domainStateFlow: StateFlow<DomainState>
) {
    private val saveStateTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    init {
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        scope.launch {
            saveStateTrigger.debounce(500).collect {
                if (configRepo.currentConfig.restore_last_state) {
                    val leversToSave = domainStateFlow.value.frames.map { f -> f.levers.map { it.isReversed } }
                    val blocksToSave = domainStateFlow.value.frames.map { f -> f.blocks.map { it.isOccupied } }
                    persistenceRepo.saveCurrentStates(SavedStatesData(tabs = leversToSave, blocks = blocksToSave))
                }
            }
        }
    }

    fun triggerSave() {
        if (configRepo.currentConfig.restore_last_state) {
            saveStateTrigger.tryEmit(Unit)
        }
    }
}
