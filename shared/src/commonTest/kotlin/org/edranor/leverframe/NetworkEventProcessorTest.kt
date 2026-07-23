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
class NetworkEventProcessorTest {

    private lateinit var configRepo: FakeConfigRepository
    private lateinit var lccClient: FakeLccClient
    private lateinit var eventProcessor: NetworkEventProcessor
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var domainState: MutableStateFlow<DomainState>

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        configRepo = FakeConfigRepository()
        lccClient = FakeLccClient()
        
        val jsonTab = JsonTab(
            name = "Test Box",
            label_lines = 2,
            label_line_height = 18,
            levers = listOf(
                JsonLever(label = "Signal", type = "HOME_SIGNAL", lcc_event_normal = "11.22.33.44.00.00.00.01", lcc_event_reversed = "11.22.33.44.00.00.00.02")
            ),
            blocks = listOf(
                JsonBlock(label = "Platform 1", lcc_event_occupied = "11.22.33.44.00.00.00.06", lcc_event_empty = "11.22.33.44.00.00.00.07")
            )
        )
        configRepo.currentConfig = JsonConfig(
            node_id = "11.22.33.44.00.00",
            tabs = listOf(jsonTab)
        )
        
        eventProcessor = NetworkEventProcessor(lccClient, configRepo)
        
        val parsedTabs = configRepo.parseConfig(configRepo.toJsonString())
        val initialFrames = parsedTabs.mapIndexed { tabIdx, tab ->
            DomainFrame(
                id = tabIdx,
                levers = tab.second.levers.mapIndexed { leverIdx, leverDef ->
                    DomainLever(id = leverIdx, isReversed = false)
                },
                blocks = tab.second.blocks.mapIndexed { blockIdx, blockDef ->
                    DomainBlock(id = blockIdx, isOccupied = true)
                }
            )
        }
        domainState = MutableStateFlow(DomainState(frames = initialFrames))
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testProcessLeverEvent() = runTest {
        val configState = ConfigState(tabs = configRepo.parseConfig(configRepo.toJsonString()))
        val uiState = TransientUiState()
        val result = eventProcessor.processEvent("1122334400000002", domainState.value, configState, uiState)
        assertTrue(result.newState.frames[0].levers[0].isReversed, "Signal lever should be reversed by external event")
    }

    @Test
    fun testProcessBlockEvent() = runTest {
        assertTrue(domainState.value.frames[0].blocks[0].isOccupied, "Block starts OCCUPIED")
        
        val configState = ConfigState(tabs = configRepo.parseConfig(configRepo.toJsonString()))
        val uiState = TransientUiState()
        val result = eventProcessor.processEvent("1122334400000007", domainState.value, configState, uiState)
        assertFalse(result.newState.frames[0].blocks[0].isOccupied, "Block should be EMPTY")
    }
}
