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
        println("Waiting for AMD")
        val msg1 = readGridConnectMessage(readChannel)
        println("Got msg1: $msg1")
        
        println("Waiting for Init Complete")
        val msg2 = readGridConnectMessage(readChannel)
        println("Got msg2: $msg2")
        
        println("Waiting for Producer Identified")
        val msg3 = readGridConnectMessage(readChannel)
        println("Got msg3: $msg3")

        // Clean nodeId is "02010D000005"
        assertTrue(msg1.contains("0700"), "Expected AMD (0700), got: $msg1")
        assertTrue(msg1.contains("02010D000005"), "Expected Node ID in AMD, got: $msg1")
        
        assertTrue(msg2.contains("19087"), "Expected Init Complete (19087), got: $msg2")
        assertTrue(msg2.contains("02010D000005"), "Expected Node ID in Init Complete, got: $msg2")
        
        assertTrue(msg3.contains("19544"), "Expected Producer Identified (19544), got: $msg3")
        assertTrue(msg3.contains("0102030405060708"), "Expected Event ID in Producer Identified, got: $msg3")


        // Extract the alias from AMD (AMD format: :X10700[ALIAS]N[NODEID];)
        // E.g., :X1070012AN02010D000005; -> index 7 is where alias starts, length 3
        val engineAlias = msg1.substring(7, 10)
        val testClientAlias = "999"

        // Trigger an event production
        println("Calling produceEvent")
        OpenLcbEngine.produceEvent("AA.BB.CC.DD.EE.FF.11.22")
        println("Called produceEvent")
        
        // Read the generated PCER
        println("Waiting for PCER")
        val pcerMsg = readGridConnectMessage(readChannel)
        println("Got pcerMsg: $pcerMsg")
        assertTrue(pcerMsg.contains("195B4"), "Expected PCER (195B4), got: $pcerMsg")
        assertTrue(pcerMsg.contains("AABBCCDDEEFF1122"), "Expected Event ID in PCER, got: $pcerMsg")
        
        // --- Test Datagram Memory Read ---
        println("Sending read request")
        val writeChannel = socket.openWriteChannel(autoFlush = true)
        val readRequest = ":X1A${engineAlias}${testClientAlias}N20410000000003;\n"
        writeChannel.writeStringUtf8(readRequest)
        
        // 1. Should receive Datagram Received OK (MTI 0x0A28 -> CAN 19A28)
        println("Waiting for Ack")
        val ackMsg = readGridConnectMessage(readChannel)
        println("Got ackMsg: $ackMsg")
        assertTrue(ackMsg.contains("19A28"), "Expected Datagram Received OK, got: $ackMsg")
        
        // 2. Should receive Datagram Read Reply
        println("Waiting for Datagram frame 1")
        val replyFrame1 = readGridConnectMessage(readChannel)
        println("Got replyFrame1: $replyFrame1")
        assertTrue(replyFrame1.contains("1B"), "Expected First Datagram Frame (1B), got: $replyFrame1")
        
        println("Waiting for Datagram frame 2")
        val replyFrame2 = readGridConnectMessage(readChannel)
        println("Got replyFrame2: $replyFrame2")
        assertTrue(replyFrame2.contains("1D"), "Expected Last Datagram Frame (1D), got: $replyFrame2")

        
        socket.close()
        OpenLcbEngine.disconnect()
        selectorManager.close()
    }
}
