package org.edranor.openlcb

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class OpenLcbEngineTest {

    class FakeConfig : OpenLcbConfig {
        override val nodeId = "02.01.0D.00.00.05"
        override val nodeName = "TestNode"
        override val jmriHubIp = "" // Force server mode
    }

    class FakeMemoryHandler : MemorySpaceHandler {
        override fun getCdiXml() = "<cdi></cdi>".encodeToByteArray()
        override fun readMemorySpace(space: Int) = byteArrayOf(1, 2, 3)
        override fun writeMemorySpace(space: Int, address: Long, data: ByteArray) {}
    }

    class FakeEventProvider : EventProducerProvider {
        override fun getProducedEvents() = listOf("01.02.03.04.05.06.07.08")
    }

    private suspend fun readGridConnectMessage(readChannel: ByteReadChannel): String {
        val sb = StringBuilder()
        while (true) {
            val b = readChannel.readByte().toInt().toChar()
            sb.append(b)
            if (b == ';') break
        }
        return sb.toString()
    }

    @Test
    fun testInitializationAndBootSequence() = runBlocking {
        OpenLcbEngine.configure(FakeConfig(), FakeMemoryHandler(), FakeEventProvider())
        OpenLcbEngine.initialize()

        delay(1000) // Wait for server to start

        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect("127.0.0.1", 12021)
        val readChannel = socket.openReadChannel()

        // Give it time for OpenLcbEngine to detect the connection and send the boot sequence
        delay(1000)

        // Read the boot sequence: AMD, Init Complete, Producer Identified
        val msg1 = readGridConnectMessage(readChannel)
        val msg2 = readGridConnectMessage(readChannel)
        val msg3 = readGridConnectMessage(readChannel)

        // Clean nodeId is "02010D000005"
        assertTrue(msg1.contains("0701"), "Expected AMD (0701), got: $msg1")
        assertTrue(msg1.contains("02010D000005"), "Expected Node ID in AMD, got: $msg1")
        
        assertTrue(msg2.contains("19100"), "Expected Init Complete (19100), got: $msg2")
        assertTrue(msg2.contains("02010D000005"), "Expected Node ID in Init Complete, got: $msg2")
        
        assertTrue(msg3.contains("19544"), "Expected Producer Identified (19544), got: $msg3")
        assertTrue(msg3.contains("0102030405060708"), "Expected Event ID in Producer Identified, got: $msg3")


        // Extract the alias from AMD (AMD format: :X10701[ALIAS]N[NODEID];)
        // E.g., :X1070112AN02010D000005; -> index 7 is where alias starts, length 3
        val engineAlias = msg1.substring(7, 10)
        val testClientAlias = "999"

        // Trigger an event production
        OpenLcbEngine.produceEvent("AA.BB.CC.DD.EE.FF.11.22")
        
        // Read the generated PCER
        val pcerMsg = readGridConnectMessage(readChannel)
        assertTrue(pcerMsg.contains("195B4"), "Expected PCER (195B4), got: $pcerMsg")
        assertTrue(pcerMsg.contains("AABBCCDDEEFF1122"), "Expected Event ID in PCER, got: $pcerMsg")
        
        // --- Test Datagram Memory Read ---
        // Protocol 0x20, Read(0x40) | space(1) -> 0x41, Address 00 00 00 00, Length 03
        // GridConnect single-frame datagram: :X1A[engineAlias][testAlias]N20410000000003;
        val writeChannel = socket.openWriteChannel(autoFlush = true)
        val readRequest = ":X1A${engineAlias}${testClientAlias}N20410000000003;\n"
        writeChannel.writeStringUtf8(readRequest)
        
        // 1. Should receive Datagram Received OK (MTI 0x0A28 -> CAN 19A28)
        val ackMsg = readGridConnectMessage(readChannel)
        assertTrue(ackMsg.contains("19A28"), "Expected Datagram Received OK, got: $ackMsg")
        
        // 2. Should receive Datagram Read Reply (Protocol 0x20, 0x51, Addr 00000000, Data 010203)
        // Reply might be split if > 8 bytes payload, but this is exactly 8 bytes (20 51 00 00 00 00 01 02) + 3rd byte -> wait, it's 9 bytes payload: 20 51 00 00 00 00 01 02 03
        // So it will be multi-frame: 1B (first), 1D (last)
        val replyFrame1 = readGridConnectMessage(readChannel)
        assertTrue(replyFrame1.contains("1B"), "Expected First Datagram Frame (1B), got: $replyFrame1")
        
        val replyFrame2 = readGridConnectMessage(readChannel)
        assertTrue(replyFrame2.contains("1D"), "Expected Last Datagram Frame (1D), got: $replyFrame2")

        
        socket.close()
        OpenLcbEngine.disconnect()
        selectorManager.close()
    }
}
