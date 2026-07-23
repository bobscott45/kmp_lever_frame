import re

def patch_file(filepath, replacements):
    with open(filepath, 'r') as f:
        content = f.read()
    for old, new in replacements:
        if old not in content:
            print(f"WARNING: could not find '{old}' in {filepath}")
        content = content.replace(old, new)
    with open(filepath, 'w') as f:
        f.write(content)

# App.kt
app_replacements = [
    (
        '''val leverStates = domainState.leverStates
            val manualLocks = domainState.manualLocks
            val blockStates = domainState.blockStates''',
        '''val frames = domainState.frames'''
    ),
    (
        '''                leverStates = leverStates,
                manualLocks = manualLocks,
                blockStates = blockStates,''',
        '''                frames = frames,'''
    ),
    (
        '''    leverStates: List<BooleanArray>,
    manualLocks: List<BooleanArray>,
    blockStates: List<BooleanArray>,''',
        '''    frames: List<DomainFrame>,'''
    ),
    (
        '''                    leverStates = leverStates[index],
                    manualLocks = manualLocks[index],''',
        '''                    levers = frames[index].levers,'''
    ),
    (
        '''                    blockStates = blockStates[index],''',
        '''                    blocks = frames[index].blocks,'''
    ),
    (
        '''    leverStates: BooleanArray,
    manualLocks: BooleanArray,''',
        '''    levers: List<DomainLever>,'''
    ),
    (
        '''                    val leverState = leverStates[index]
                    val isLocked = manualLocks[index]''',
        '''                    val leverState = levers[index].isReversed
                    val isLocked = levers[index].isManuallyLocked'''
    ),
    (
        '''    blockStates: BooleanArray,''',
        '''    blocks: List<DomainBlock>,'''
    ),
    (
        '''                    val isOccupied = blockStates[index]''',
        '''                    val isOccupied = blocks[index].isOccupied'''
    ),
    (
        '''                val states = leverStates[index]
                val blocks = blockStates[index]''',
        '''                val states = frames[index].levers.map { it.isReversed }.toBooleanArray()
                val blocks = frames[index].blocks.map { it.isOccupied }.toBooleanArray()'''
    )
]
patch_file('shared/src/commonMain/kotlin/org/edranor/leverframe/App.kt', app_replacements)

# AppViewModelTest.kt
avmt_replacements = [
    (
        '''assertEquals(false, viewModel.domainState.value.leverStates[0][2])''',
        '''assertEquals(false, viewModel.domainState.value.frames[0].levers[2].isReversed)'''
    ),
    (
        '''assertEquals(true, viewModel.domainState.value.leverStates[0][2])''',
        '''assertEquals(true, viewModel.domainState.value.frames[0].levers[2].isReversed)'''
    ),
    (
        '''assertEquals(false, viewModel.domainState.value.manualLocks[0][1])''',
        '''assertEquals(false, viewModel.domainState.value.frames[0].levers[1].isManuallyLocked)'''
    ),
    (
        '''assertEquals(true, viewModel.domainState.value.manualLocks[0][1])''',
        '''assertEquals(true, viewModel.domainState.value.frames[0].levers[1].isManuallyLocked)'''
    ),
    (
        '''assertEquals(false, viewModel.domainState.value.blockStates[0][0])''',
        '''assertEquals(false, viewModel.domainState.value.frames[0].blocks[0].isOccupied)'''
    ),
    (
        '''assertEquals(true, viewModel.domainState.value.blockStates[0][0])''',
        '''assertEquals(true, viewModel.domainState.value.frames[0].blocks[0].isOccupied)'''
    )
]
patch_file('shared/src/commonTest/kotlin/org/edranor/leverframe/AppViewModelTest.kt', avmt_replacements)

# LeverFramePolicyTest.kt
lfpt_replacements = [
    (
        '''val states = booleanArrayOf(false, false, false)''',
        '''val levers = listOf(DomainLever(0, false), DomainLever(1, false), DomainLever(2, false))'''
    ),
    (
        '''val blockStates = booleanArrayOf()''',
        '''val blocks = emptyList<DomainBlock>()'''
    ),
    (
        '''assertNotNull(LeverFramePolicy.attemptToggle(tabDef, states, blockStates, 0, true))''',
        '''assertNotNull(LeverFramePolicy.attemptToggle(tabDef, levers, blocks, 0, true))'''
    ),
    (
        '''assertNull(LeverFramePolicy.attemptToggle(tabDef, states, blockStates, 1, true))''',
        '''assertNull(LeverFramePolicy.attemptToggle(tabDef, levers, blocks, 1, true))'''
    )
]
patch_file('shared/src/commonTest/kotlin/org/edranor/leverframe/LeverFramePolicyTest.kt', lfpt_replacements)

