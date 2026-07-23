import re

# LeverFramePolicyTest.kt
with open('shared/src/commonTest/kotlin/org/edranor/leverframe/LeverFramePolicyTest.kt', 'r') as f:
    content = f.read()
content = content.replace(
    'val states = booleanArrayOf(false, false, false)',
    'val levers = listOf(DomainLever(0, false), DomainLever(1, false), DomainLever(2, false))'
)
content = content.replace(
    'val blockStates = booleanArrayOf()',
    'val blocks = emptyList<DomainBlock>()'
)
content = content.replace(
    'states, blockStates',
    'levers, blocks'
)
with open('shared/src/commonTest/kotlin/org/edranor/leverframe/LeverFramePolicyTest.kt', 'w') as f:
    f.write(content)

# AppViewModelTest.kt
with open('shared/src/commonTest/kotlin/org/edranor/leverframe/AppViewModelTest.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'viewModel.domainState.value.leverStates[0]',
    'viewModel.domainState.value.frames[0].levers.map { it.isReversed }.toBooleanArray()'
)
content = content.replace(
    'viewModel.domainState.value.blockStates[0]',
    'viewModel.domainState.value.frames[0].blocks.map { it.isOccupied }.toBooleanArray()'
)
content = content.replace(
    'viewModel.domainState.value.manualLocks[0]',
    'viewModel.domainState.value.frames[0].levers.map { it.isManuallyLocked }.toBooleanArray()'
)
with open('shared/src/commonTest/kotlin/org/edranor/leverframe/AppViewModelTest.kt', 'w') as f:
    f.write(content)

# App.kt
with open('shared/src/commonMain/kotlin/org/edranor/leverframe/App.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val leverStates = domainState.leverStates',
    'val frames = domainState.frames'
)
content = content.replace(
    'val manualLocks = domainState.manualLocks',
    ''
)
content = content.replace(
    'val blockStates = domainState.blockStates',
    ''
)
content = content.replace(
    'leverStates = leverStates',
    'frames = frames'
)
content = content.replace(
    'manualLocks = manualLocks,',
    ''
)
content = content.replace(
    'blockStates = blockStates,',
    ''
)
content = content.replace(
    'leverStates: List<BooleanArray>,',
    'frames: List<DomainFrame>,'
)
content = content.replace(
    'manualLocks: List<BooleanArray>,',
    ''
)
content = content.replace(
    'blockStates: List<BooleanArray>,',
    ''
)

# LeverTrackGroup
content = content.replace(
    'leverStates: BooleanArray,',
    'levers: List<DomainLever>,'
)
content = content.replace(
    'manualLocks: BooleanArray,',
    ''
)
content = content.replace(
    'val leverState = leverStates[index]',
    'val leverState = levers[index].isReversed'
)
content = content.replace(
    'val isLocked = manualLocks[index]',
    'val isLocked = levers[index].isManuallyLocked'
)

# App calls to LeverTrackGroup
content = content.replace(
    'leverStates = leverStates[index],',
    'levers = frames[index].levers,'
)
content = content.replace(
    'manualLocks = manualLocks[index],',
    ''
)

# BlockShelfGroup
content = content.replace(
    'blockStates: BooleanArray,',
    'blocks: List<DomainBlock>,'
)
content = content.replace(
    'val isOccupied = blockStates[index]',
    'val isOccupied = blocks[index].isOccupied'
)

# App calls to BlockShelfGroup
content = content.replace(
    'blockStates = blockStates[index],',
    'blocks = frames[index].blocks,'
)

# Schematic
content = content.replace(
    'val states = leverStates[index]',
    'val states = frames[index].levers.map { it.isReversed }.toBooleanArray()'
)
content = content.replace(
    'val blocks = blockStates[index]',
    'val blocks = frames[index].blocks.map { it.isOccupied }.toBooleanArray()'
)

with open('shared/src/commonMain/kotlin/org/edranor/leverframe/App.kt', 'w') as f:
    f.write(content)
