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
        val newFrames = currentDomain.frames.toMutableList()
        val policy = ConflictPolicy.of(configState.config.conflict_policy)

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
                    val frame = newFrames[tabIdx]
                    val currState = frame.levers[leverIdx].isReversed
                    if (currState != attemptState) {
                        val isValid = Interlocking.evaluate(tabDef, frame.levers, frame.blocks, leverIdx, attemptState)
                        if (LeverFramePolicy.shouldApplyExternalEvent(policy, isValid)) {
                            val newLevers = frame.levers.toMutableList()
                            newLevers[leverIdx] = newLevers[leverIdx].copy(isReversed = attemptState)
                            newFrames[tabIdx] = frame.copy(levers = newLevers)
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
                    val frame = newFrames[tabIdx]
                    if (frame.blocks[blockIdx].isOccupied != attemptBlockState) {
                        val newBlocks = frame.blocks.toMutableList()
                        newBlocks[blockIdx] = newBlocks[blockIdx].copy(isOccupied = attemptBlockState)
                        newFrames[tabIdx] = frame.copy(blocks = newBlocks)
                        stateChanged = true
                    }
                }
            }

            // Evaluate auto-reversers (cascade until steady state)
            val mutableLevers = newFrames[tabIdx].levers.toMutableList()
            if (Interlocking.applyCascades(tabDef, mutableLevers, newFrames[tabIdx].blocks, outgoingEvents)) {
                newFrames[tabIdx] = newFrames[tabIdx].copy(levers = mutableLevers)
                stateChanged = true
            }
        } // end forEachIndexed

        if (stateChanged) {
            val conflicts = if (configState.tabs.isNotEmpty() && uiState.selectedTabIndex in newFrames.indices) {
                Interlocking.getConflictingLevers(
                    configState.tabs[uiState.selectedTabIndex].second,
                    newFrames[uiState.selectedTabIndex].levers,
                    newFrames[uiState.selectedTabIndex].blocks
                )
            } else emptyList()
            stateToReturn = stateToReturn.copy(frames = newFrames, conflictingLevers = conflicts)
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
