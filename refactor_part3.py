import re

# PersistenceService.kt
with open('shared/src/commonMain/kotlin/org/edranor/leverframe/PersistenceService.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val statesToSave = domainStateFlow.value.leverStates.map { it.copyOf() }',
    'val statesToSave = domainStateFlow.value.frames.map { f -> f.levers.map { it.isReversed }.toBooleanArray() }'
)
with open('shared/src/commonMain/kotlin/org/edranor/leverframe/PersistenceService.kt', 'w') as f:
    f.write(content)

# NetworkEventProcessor.kt
with open('shared/src/commonMain/kotlin/org/edranor/leverframe/NetworkEventProcessor.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val newLeverStates = currentDomain.leverStates.map { it.copyOf() }',
    'val newFrames = currentDomain.frames.toMutableList()'
)
content = content.replace(
    'val newBlockStates = currentDomain.blockStates.map { it.copyOf() }.toMutableList()',
    ''
)
content = content.replace(
    'val currState = newLeverStates[tabIdx][leverIdx]',
    'val currState = newFrames[tabIdx].levers[leverIdx].isReversed'
)
content = content.replace(
    'val isValid = Interlocking.evaluate(tabDef, newLeverStates[tabIdx], currentDomain.blockStates[tabIdx], leverIdx, attemptState)',
    'val isValid = Interlocking.evaluate(tabDef, newFrames[tabIdx].levers, currentDomain.frames[tabIdx].blocks, leverIdx, attemptState)'
)
content = content.replace(
    'newLeverStates[tabIdx][leverIdx] = attemptState',
    'val updatedLevers = newFrames[tabIdx].levers.toMutableList()\n                            updatedLevers[leverIdx] = updatedLevers[leverIdx].copy(isReversed = attemptState)\n                            newFrames[tabIdx] = newFrames[tabIdx].copy(levers = updatedLevers)'
)
content = content.replace(
    'if (newBlockStates[tabIdx][blockIdx] != attemptBlockState) {',
    'if (newFrames[tabIdx].blocks[blockIdx].isOccupied != attemptBlockState) {'
)
content = content.replace(
    'newBlockStates[tabIdx][blockIdx] = attemptBlockState',
    'val updatedBlocks = newFrames[tabIdx].blocks.toMutableList()\n                        updatedBlocks[blockIdx] = updatedBlocks[blockIdx].copy(isOccupied = attemptBlockState)\n                        newFrames[tabIdx] = newFrames[tabIdx].copy(blocks = updatedBlocks)'
)
content = content.replace(
    'val currentConflicts = Interlocking.getConflictingLevers(tabDef, newLeverStates[tabIdx], newBlockStates[tabIdx])',
    'val currentConflicts = Interlocking.getConflictingLevers(tabDef, newFrames[tabIdx].levers, newFrames[tabIdx].blocks)'
)
content = content.replace(
    'if (leverDef.autoReverser && newLeverStates[tabIdx][leverIdx]) {',
    'if (leverDef.autoReverser && newFrames[tabIdx].levers[leverIdx].isReversed) {'
)
content = content.replace(
    'newLeverStates[tabIdx][leverIdx] = false',
    'val updatedLevers = newFrames[tabIdx].levers.toMutableList()\n                                updatedLevers[leverIdx] = updatedLevers[leverIdx].copy(isReversed = false)\n                                newFrames[tabIdx] = newFrames[tabIdx].copy(levers = updatedLevers)'
)
content = content.replace(
    'if (stateChanged && newBlockStates !== currentDomain.blockStates) {\n                stateToReturn = stateToReturn.copy(blockStates = newBlockStates)\n            }',
    ''
)
content = content.replace(
    'newLeverStates[uiState.selectedTabIndex]',
    'newFrames[uiState.selectedTabIndex].levers'
)
content = content.replace(
    'newBlockStates[uiState.selectedTabIndex]',
    'newFrames[uiState.selectedTabIndex].blocks'
)
content = content.replace(
    'stateToReturn = stateToReturn.copy(leverStates = newLeverStates, conflictingLevers = conflicts)',
    'stateToReturn = stateToReturn.copy(frames = newFrames, conflictingLevers = conflicts)'
)
with open('shared/src/commonMain/kotlin/org/edranor/leverframe/NetworkEventProcessor.kt', 'w') as f:
    f.write(content)

# LeverFramePolicy.kt
with open('shared/src/commonMain/kotlin/org/edranor/leverframe/LeverFramePolicy.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'fun attemptToggle(tab: TabDef, states: BooleanArray, blockStates: BooleanArray, leverIndex: Int, attemptingState: Boolean): BooleanArray?',
    'fun attemptToggle(tab: TabDef, levers: List<DomainLever>, blocks: List<DomainBlock>, leverIndex: Int, attemptingState: Boolean): List<DomainLever>?'
)
content = content.replace(
    'val isValid = Interlocking.evaluate(tab, states, blockStates, leverIndex, attemptingState)',
    'val isValid = Interlocking.evaluate(tab, levers, blocks, leverIndex, attemptingState)'
)
content = content.replace(
    'val newStates = states.copyOf()\n        newStates[leverIndex] = attemptingState\n        return newStates',
    'val newLevers = levers.toMutableList()\n        newLevers[leverIndex] = newLevers[leverIndex].copy(isReversed = attemptingState)\n        return newLevers'
)
with open('shared/src/commonMain/kotlin/org/edranor/leverframe/LeverFramePolicy.kt', 'w') as f:
    f.write(content)
