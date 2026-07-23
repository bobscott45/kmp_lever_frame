package org.edranor.leverframe

class NetworkEventProcessor(
    private val lccClient: LccNetworkClient,
    private val configRepo: AppConfigRepository
) {

    fun processEvent(
        hexEventId: String,
        currentDomain: DomainState,
        configState: ConfigState,
        uiState: TransientUiState
    ): EventProcessorResult {
        if (!configRepo.currentConfig.lcc_master) {
            return EventProcessorResult(didChange = false, newState = currentDomain, outgoingEvents = emptyList())
        }

        var stateChanged = false
        var stateToReturn = currentDomain
        val outgoingEvents = mutableListOf<String>()
        val newLeverStates = currentDomain.leverStates.map { it.copyOf() }
        val policy = ConflictPolicy.of(configState.config.conflict_policy)
        val newBlockStates = currentDomain.blockStates.map { it.copyOf() }.toMutableList()

        configState.tabs.forEachIndexed { tabIdx, tabPair ->
            val tabDef = tabPair.second
            tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                if (!leverDef.lcc_enabled) return@forEachIndexed
                var attemptState: Boolean? = null

                if (leverDef.lcc_event_normal.isNotBlank()) {
                    val normalHex = lccClient.parseEventId(leverDef.lcc_event_normal)
                    if (normalHex == hexEventId) attemptState = false
                }
                if (leverDef.lcc_event_reversed.isNotBlank()) {
                    val reversedHex = lccClient.parseEventId(leverDef.lcc_event_reversed)
                    if (reversedHex == hexEventId) attemptState = true
                }

                if (attemptState != null) {
                    val currState = newLeverStates[tabIdx][leverIdx]
                    if (currState != attemptState) {
                        val isValid = Interlocking.evaluate(tabDef, newLeverStates[tabIdx], currentDomain.blockStates[tabIdx], leverIdx, attemptState)
                        if (LeverFramePolicy.shouldApplyExternalEvent(policy, isValid)) {
                            newLeverStates[tabIdx][leverIdx] = attemptState
                            stateChanged = true
                        }
                    }
                }
            }
            
            // Handle Block states
            tabDef.blocks.forEachIndexed { blockIdx, blockDef ->
                var attemptBlockState: Boolean? = null
                if (blockDef.lcc_event_occupied.isNotBlank()) {
                    val occupiedHex = lccClient.parseEventId(blockDef.lcc_event_occupied)
                    if (occupiedHex == hexEventId) attemptBlockState = true
                }
                if (blockDef.lcc_event_empty.isNotBlank()) {
                    val emptyHex = lccClient.parseEventId(blockDef.lcc_event_empty)
                    if (emptyHex == hexEventId) attemptBlockState = false
                }
                if (attemptBlockState != null) {
                    if (newBlockStates[tabIdx][blockIdx] != attemptBlockState) {
                        newBlockStates[tabIdx][blockIdx] = attemptBlockState
                        stateChanged = true
                    }
                }
            }

            // Evaluate auto-reversers (cascade until steady state)
            var reverserChanged: Boolean
            do {
                reverserChanged = false
                val currentConflicts = Interlocking.getConflictingLevers(tabDef, newLeverStates[tabIdx], newBlockStates[tabIdx])

                tabDef.levers.forEachIndexed { leverIdx, leverDef ->
                    if (leverDef.autoReverser && newLeverStates[tabIdx][leverIdx]) {
                        if (leverIdx in currentConflicts) {
                            newLeverStates[tabIdx][leverIdx] = false // Force to NORMAL
                            stateChanged = true
                            reverserChanged = true
                            if (leverDef.lcc_event_normal.isNotBlank()) {
                                outgoingEvents.add(leverDef.lcc_event_normal)
                            }
                        }
                    }
                }
            } while (reverserChanged)

            if (stateChanged && newBlockStates !== currentDomain.blockStates) {
                stateToReturn = stateToReturn.copy(blockStates = newBlockStates)
            }
        }

        if (stateChanged) {
            val conflicts = if (configState.tabs.isNotEmpty()) {
                Interlocking.getConflictingLevers(
                    configState.tabs[uiState.selectedTabIndex].second,
                    newLeverStates[uiState.selectedTabIndex],
                    newBlockStates[uiState.selectedTabIndex]
                )
            } else emptyList()
            stateToReturn = stateToReturn.copy(leverStates = newLeverStates, conflictingLevers = conflicts)
        }

        return EventProcessorResult(
            didChange = stateChanged,
            newState = stateToReturn,
            outgoingEvents = outgoingEvents
        )
    }
}

data class EventProcessorResult(
    val didChange: Boolean,
    val newState: DomainState,
    val outgoingEvents: List<String>
)
