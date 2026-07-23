package org.edranor.leverframe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalCoroutinesApi::class)
class PersistenceServiceTest {

    private lateinit var configRepo: FakeConfigRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        configRepo = FakeConfigRepository()
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStateIsSavedWithDebounce() = runTest(testDispatcher) {
        val initialFrames = listOf(
            DomainFrame(
                id = 0,
                levers = listOf(DomainLever(id = 0, isReversed = true)),
                blocks = listOf(DomainBlock(id = 0, isOccupied = false))
            )
        )
        val domainState = MutableStateFlow(DomainState(frames = initialFrames))
        
        val persistenceService = PersistenceService(configRepo, backgroundScope, domainState)
        
        testDispatcher.scheduler.runCurrent()
        persistenceService.triggerSave()
        
        // Wait for initial debounce to settle
        testDispatcher.scheduler.advanceTimeBy(1000)
        testDispatcher.scheduler.runCurrent()
        
        val savedData = configRepo.savedLeverStates
        assertTrue(savedData != null)
        assertTrue(savedData.tabs[0][0]) // Lever 0 reversed
        assertFalse(savedData.blocks[0][0]) // Block 0 empty
    }
}
