package org.edranor.leverframe

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class PersistenceService(
    private val configRepo: AppConfigRepository,
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
                    configRepo.saveCurrentStates(SavedStatesData(tabs = leversToSave, blocks = blocksToSave))
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
