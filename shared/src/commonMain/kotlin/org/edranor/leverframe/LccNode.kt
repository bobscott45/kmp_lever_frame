/*
 * Copyright (C) 2026 Robert Scott
 *
 * This file is part of LeverFrame.
 *
 * This project is dual-licensed to balance open-source collaboration with 
 * ecosystem compatibility:
 *
 * * Source Code: The source code in this repository is licensed under the 
 *   GNU General Public License v3 (GPLv3). You are free to copy, modify, 
 *   and self-compile the code, provided any distributions remain open-source 
 *   under the same terms.
 * * Compiled Binaries & Storefronts: As the sole copyright owner of this 
 *   codebase, the author reserves the right to distribute compiled binaries 
 *   (such as on the Apple App Store, Google Play, or other platforms) under 
 *   separate, proprietary, or storefront-specific licenses.
 *
 * Note: If you wish to contribute code to this project via a Pull Request, you 
 * agree to grant the author a non-exclusive, perpetual license to distribute 
 * your contributions under both the GPLv3 and our storefront distribution licenses.
 */
package org.edranor.leverframe

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharedFlow

interface LccNetworkClient {
    val externalEvents: SharedFlow<String>
    val connectionStatus: kotlinx.coroutines.flow.StateFlow<String>
    val connectionErrors: SharedFlow<String>
    fun initialize()
    fun disconnect()
    fun produceEvent(eventIdStr: String)
    fun parseEventId(eventIdStr: String): String
    fun identifyProducer(eventIdStr: String)
}

object LccNode : LccNetworkClient {

    private var NODE_ALIAS = "12A" // Using a fixed alias for simplicity, though real nodes allocate it dynamically
    
    private var lccJob: Job? = null
    
    internal val datagramBuffers = mutableMapOf<String, MutableList<Byte>>()
    
    internal fun getCdiXml(): ByteArray {
        val config = ConfigManager.currentConfig
        val tabGroups = StringBuilder()
        var currentOffset = 3
        for (tab in config.tabs) {
            val numLevers = tab.levers.size
            val numBlocks = tab.blocks.size
            if (numLevers > 0 || numBlocks > 0) {
                tabGroups.append("""
    <group offset="$currentOffset">
        <name>${tab.name}</name>
        <description>Configuration for ${tab.name}</description>""")
        
                var tabOffset = 0
                if (numLevers > 0) {
                    tabGroups.append("""
        <group offset="$tabOffset" replication="$numLevers">
            <name>Levers</name>
            <repname>Lever</repname>
            <eventid><name>Event Normal</name></eventid>
            <eventid><name>Event Reversed</name></eventid>
        </group>""")
                    tabOffset += numLevers * 16
                }
                
                if (numBlocks > 0) {
                    tabGroups.append("""
        <group offset="$tabOffset" replication="$numBlocks">
            <name>Blocks</name>
            <repname>Block</repname>
            <eventid><name>Event Occupied</name></eventid>
            <eventid><name>Event Empty</name></eventid>
        </group>""")
                    tabOffset += numBlocks * 16
                }
                
                tabGroups.append("""
    </group>""")
                currentOffset += tabOffset
            }
        }
        
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<cdi xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://openlcb.org/schema/cdi/1/1/cdi.xsd">
<identification>
<manufacturer>Edranor</manufacturer>
<model>LeverFrame Node</model>
<hardwareVersion>1.0</hardwareVersion>
<softwareVersion>1.2.0-dev</softwareVersion>
</identification>
<segment space="253" origin="0">
    <group>
        <name>Network Toggles</name>
        <description>General node settings</description>
        <int size="1">
            <name>LCC Master Mode</name>
            <description>1 = Master (Broadcasts events), 0 = Slave (Only listens)</description>
            <min>0</min><max>1</max><default>1</default>
        </int>
        <int size="1">
            <name>LCC Enabled</name>
            <description>1 = Enabled, 0 = Disabled</description>
            <min>0</min><max>1</max><default>1</default>
        </int>
        <int size="1">
            <name>Restore Last State</name>
            <description>1 = Restore last state on boot, 0 = Normal</description>
            <min>0</min><max>1</max><default>1</default>
        </int>
    </group>$tabGroups
</segment>
</cdi>"""
        return xml.encodeToByteArray() + byteArrayOf(0)
    }

    private fun parseEventIdStringToHex(eventId: String): String {
        return parseEventId(eventId).padEnd(16, '0')
    }

    internal fun buildMemorySpace(): ByteArray {
        val config = ConfigManager.currentConfig
        val numLevers = config.tabs.sumOf { it.levers.size }
        val numBlocks = config.tabs.sumOf { it.blocks.size }
        val size = 3 + numLevers * 16 + numBlocks * 16
        val buffer = ByteArray(size)
        
        buffer[0] = if (config.lcc_master) 1 else 0
        buffer[1] = if (config.lcc_enabled) 1 else 0
        buffer[2] = if (config.restore_last_state) 1 else 0
        
        var offset = 3
        for (tab in config.tabs) {
            for (lever in tab.levers) {
                val normHex = parseEventIdStringToHex(lever.lcc_event_normal)
                val revHex = parseEventIdStringToHex(lever.lcc_event_reversed)
                val normBytes = normHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val revBytes = revHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                normBytes.copyInto(buffer, offset)
                revBytes.copyInto(buffer, offset + 8)
                offset += 16
            }
            for (block in tab.blocks) {
                val occHex = parseEventIdStringToHex(block.lcc_event_occupied)
                val empHex = parseEventIdStringToHex(block.lcc_event_empty)
                val occBytes = occHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val empBytes = empHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                occBytes.copyInto(buffer, offset)
                empBytes.copyInto(buffer, offset + 8)
                offset += 16
            }
        }
        return buffer
    }

    internal fun applyMemorySpace(buffer: ByteArray) {
        var config = ConfigManager.currentConfig
        
        if (buffer.size >= 3) {
            config = config.copy(
                lcc_master = buffer[0].toInt() != 0,
                lcc_enabled = buffer[1].toInt() != 0,
                restore_last_state = buffer[2].toInt() != 0
            )
        }
        
        var offset = 3
        val newTabs = config.tabs.map { tab ->
            val newLevers = tab.levers.map { lever ->
                if (offset + 16 <= buffer.size) {
                    val normEvent = buffer.copyOfRange(offset, offset + 8).joinToString(".") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    val revEvent = buffer.copyOfRange(offset + 8, offset + 16).joinToString(".") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    offset += 16
                    lever.copy(lcc_event_normal = normEvent, lcc_event_reversed = revEvent)
                } else {
                    lever
                }
            }
            
            val newBlocks = tab.blocks.map { block ->
                if (offset + 16 <= buffer.size) {
                    val occEvent = buffer.copyOfRange(offset, offset + 8).joinToString(".") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    val empEvent = buffer.copyOfRange(offset + 8, offset + 16).joinToString(".") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    offset += 16
                    block.copy(lcc_event_occupied = occEvent, lcc_event_empty = empEvent)
                } else {
                    block
                }
            }
            
            tab.copy(levers = newLevers, blocks = newBlocks)
        }
        config = config.copy(tabs = newTabs)
        
        ConfigManager.currentConfig = config
        kotlinx.coroutines.MainScope().launch {
            ConfigManager.saveConfig(config)
        }
    }
    
    private val _externalEvents = MutableSharedFlow<String>(extraBufferCapacity = 100)
    override val externalEvents = _externalEvents.asSharedFlow()

    override val connectionStatus = GridConnectNetwork.connectionStatus
    override val connectionErrors = GridConnectNetwork.connectionErrors

    override fun disconnect() {
        lccJob?.cancel()
        GridConnectNetwork.stop()
    }

    override fun initialize() {
        val hubIp = ConfigManager.currentConfig.jmri_hub_ip.trim()
        
        // Generate pseudo-random alias to avoid JMRI collisions
        NODE_ALIAS = kotlin.random.Random.nextInt(1, 4096).toString(16).padStart(3, '0').uppercase()

        
        // When initialized (or a client connects), announce our presence
        GridConnectNetwork.onClientConnected = {
            CoroutineScope(Dispatchers.Default).launch {
                kotlinx.coroutines.delay(500) // Give network time to settle
                sendAliasMapDefinition()
                sendInitializationComplete()
                sendAllProducerIdentified()
            }
        }

        if (hubIp.isEmpty()) {
            GridConnectNetwork.startServer()
        } else {
            GridConnectNetwork.startClient(hubIp)
        }
        
        lccJob?.cancel()
        lccJob = CoroutineScope(Dispatchers.Default).launch {
            GridConnectNetwork.incomingMessages.collect { msgRaw ->
                val msg = msgRaw.uppercase()
                // Handle incoming GridConnect messages here if needed
                if (msg.contains("X18490") || msg.contains("X19490")) { // Verify Node ID (Global)
                    // Respond with Verified Node ID
                    sendVerifiedNodeId()
                } else if (msg.contains("X18DE8") || msg.contains("X19DE8")) { // Simple Node Info Request
                    val startIdx = if (msg.contains("X18DE8")) msg.indexOf("X18DE8") + 6 else msg.indexOf("X19DE8") + 6
                    val destAlias = msg.substring(startIdx, startIdx + 3)
                    sendSimpleNodeInfoReply(destAlias)
                } else if (msg.contains("X185B4") || msg.contains("X195B4")) { // PCER Event
                    val startIdx = if (msg.contains("X185B4")) msg.indexOf("X185B4") + 1 else msg.indexOf("X195B4") + 1
                    val nIdx = msg.indexOf("N", startIdx)
                    if (nIdx != -1 && msg.length >= nIdx + 17) {
                        val hexData = msg.substring(nIdx + 1, nIdx + 17)
                        _externalEvents.tryEmit(hexData)
                    }
                } else if (msg.contains("X18970") || msg.contains("X19970") || msg.contains("X18968") || msg.contains("X19968")) { // Identify Events Global / Addressed
                    sendAllProducerIdentified()
                } else if (msg.contains("X18914") || msg.contains("X19914")) { // Identify Producers Global/Addressed
                    sendAllProducerIdentified()
                } else if (msg.startsWith(":X19828")) { // Protocol Support Inquiry Addressed
                    val dataIdx = msg.indexOf('N')
                    if (dataIdx != -1 && msg.length >= dataIdx + 5) {
                        val destAlias = msg.substring(dataIdx + 2, dataIdx + 5)
                        if (destAlias == NODE_ALIAS) {
                            val srcAlias = msg.substring(7, 10)
                            sendProtocolSupportReply(srcAlias)
                        }
                    }
                } else if (msg.startsWith(":X1A${NODE_ALIAS}") || 
                           msg.startsWith(":X1B${NODE_ALIAS}") || 
                           msg.startsWith(":X1C${NODE_ALIAS}") || 
                           msg.startsWith(":X1D${NODE_ALIAS}")) {
                    handleIncomingDatagramFrame(msg)
                }
            }
        }
    }

    private fun getCleanNodeId(): String {
        return ConfigManager.currentConfig.node_id.replace(".", "").padStart(12, '0').uppercase()
    }

    internal fun handleIncomingDatagramFrame(msg: String) {
        try {
            if (msg.length < 13) return
            val frameType = msg.substring(2, 4) // 1A, 1B, 1C, 1D
            val sourceAlias = msg.substring(7, 10)
            val dataIdx = msg.indexOf('N')
            if (dataIdx == -1) return
            
            val hexData = msg.substring(dataIdx + 1, msg.length - 1)
            val payloadBytes = hexData.chunked(2).map { it.toInt(16).toByte() }
            
            if (frameType == "1A") { // Single frame datagram
                processDatagram(sourceAlias, payloadBytes)
            } else if (frameType == "1B") { // First frame
                datagramBuffers[sourceAlias] = payloadBytes.toMutableList()
            } else if (frameType == "1C") { // Middle frame
                datagramBuffers[sourceAlias]?.addAll(payloadBytes)
            } else if (frameType == "1D") { // Last frame
                datagramBuffers[sourceAlias]?.let {
                    it.addAll(payloadBytes)
                    processDatagram(sourceAlias, it)
                    datagramBuffers.remove(sourceAlias)
                }
            }
        } catch (e: Exception) {
            println("Failed to parse datagram frame: ${e.message}")
        }
    }

    internal fun processDatagram(sourceAlias: String, payload: List<Byte>) {
        println("Received complete datagram from $sourceAlias, length ${payload.size}")
        
        // 1. Acknowledge at the transport layer
        sendDatagramReceivedOk(sourceAlias)
        
        // 2. Process Memory Configuration Protocol (Protocol ID 0x20)
        if (payload.isNotEmpty() && payload[0].toInt() == 0x20) {
            val subCmd = payload[1].toInt() and 0xFF
            // Read Request (0x40)
            if ((subCmd and 0xC0) == 0x40 && payload.size >= 7) {
                val space = subCmd and 0x03
                val address = ((payload[2].toInt() and 0xFF).toLong() shl 24) or
                              ((payload[3].toInt() and 0xFF).toLong() shl 16) or
                              ((payload[4].toInt() and 0xFF).toLong() shl 8) or
                              ((payload[5].toInt() and 0xFF).toLong())
                val len = payload[6].toInt() and 0xFF
                
                val memorySpace = if (space == 0x03) getCdiXml() else if (space == 0x01) buildMemorySpace() else ByteArray(0)
                
                val dataChunk = if (address < memorySpace.size) {
                    val endAddr = minOf(address + len, memorySpace.size.toLong()).toInt()
                    memorySpace.sliceArray(address.toInt() until endAddr)
                } else {
                    ByteArray(0)
                }
                
                val replyPayload = mutableListOf<Byte>()
                replyPayload.add(0x20.toByte())
                replyPayload.add((0x50 or space).toByte()) // Read Reply
                replyPayload.add(payload[2])
                replyPayload.add(payload[3])
                replyPayload.add(payload[4])
                replyPayload.add(payload[5])
                replyPayload.addAll(dataChunk.toList())
                
                sendDatagram(sourceAlias, replyPayload)
            }
            // Write Request (0x00)
            else if ((subCmd and 0xC0) == 0x00 && payload.size >= 7) {
                val space = subCmd and 0x03
                val address = ((payload[2].toInt() and 0xFF).toLong() shl 24) or
                              ((payload[3].toInt() and 0xFF).toLong() shl 16) or
                              ((payload[4].toInt() and 0xFF).toLong() shl 8) or
                              ((payload[5].toInt() and 0xFF).toLong())
                              
                val writeData = payload.drop(6)
                if (space == 0x01) { // Space 253
                    val currentMem = buildMemorySpace()
                    if (address < currentMem.size) {
                        val endAddr = minOf(address + writeData.size, currentMem.size.toLong()).toInt()
                        for (i in address.toInt() until endAddr) {
                            currentMem[i] = writeData[i - address.toInt()]
                        }
                        applyMemorySpace(currentMem)
                    }
                }
                
                val replyPayload = mutableListOf<Byte>()
                replyPayload.add(0x20.toByte())
                replyPayload.add((0x10 or space).toByte()) // Write Reply
                replyPayload.add(payload[2])
                replyPayload.add(payload[3])
                replyPayload.add(payload[4])
                replyPayload.add(payload[5])
                sendDatagram(sourceAlias, replyPayload)
            }
        }
    }

    internal fun sendDatagram(destAlias: String, payload: List<Byte>) {
        try {
            if (payload.size <= 8) {
                val hexData = payload.joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                GridConnectNetwork.sendMessage(":X1A${destAlias}${NODE_ALIAS}N${hexData};")
            } else {
                val chunks = payload.chunked(8)
                for ((index, chunk) in chunks.withIndex()) {
                    val hexData = chunk.joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    if (index == 0) {
                        GridConnectNetwork.sendMessage(":X1B${destAlias}${NODE_ALIAS}N${hexData};")
                    } else if (index == chunks.lastIndex) {
                        GridConnectNetwork.sendMessage(":X1D${destAlias}${NODE_ALIAS}N${hexData};")
                    } else {
                        GridConnectNetwork.sendMessage(":X1C${destAlias}${NODE_ALIAS}N${hexData};")
                    }
                }
            }
            println("Sent outgoing datagram to $destAlias (size ${payload.size})")
        } catch (e: Exception) {
            println("Failed to send datagram: ${e.message}")
        }
    }

    private fun sendDatagramReceivedOk(destAlias: String) {
        try {
            // Datagram Received OK (MTI 0x0A28) is an addressed message.
            // In CAN, it's sent as 19A28[NODE_ALIAS]N0[destAlias]
            val msg = ":X19A28${NODE_ALIAS}N0${destAlias};"
            GridConnectNetwork.sendMessage(msg)
            println("Sent Datagram Received OK to $destAlias")
        } catch (e: Exception) {
            println("Failed to send Datagram Received OK: ${e.message}")
        }
    }

    private fun sendProtocolSupportReply(destAlias: String) {
        try {
            // Protocol Support Reply (MTI 0x0668) is an addressed message.
            // Payload: 0[destAlias] followed by 6 bytes of supported protocols.
            // Protocols supported: ProtocolIdentification, Datagram, MemoryConfiguration, ProducerConsumer, SNIP, CDI
            // Byte 0: 0xD4, Byte 1: 0x18, Bytes 2-5: 0x00
            val msg = ":X19668${NODE_ALIAS}N0${destAlias}D41800000000;"
            GridConnectNetwork.sendMessage(msg)
            println("Sent Protocol Support Reply to $destAlias")
        } catch (e: Exception) {
            println("Failed to send Protocol Support Reply: ${e.message}")
        }
    }

    private fun sendAliasMapDefinition() {
        try {
            val nodeId = getCleanNodeId()
            // AMD: MTI 0x0701 -> CAN 10701[Alias]
            val msg = ":X10701${NODE_ALIAS}N${nodeId};"
            GridConnectNetwork.sendMessage(msg)
            println("Sent AMD: $msg")
        } catch (e: Exception) {
            println("Failed to send AMD: ${e.message}")
        }
    }

    private fun sendInitializationComplete() {
        try {
            val nodeId = getCleanNodeId()
            // Initialization Complete: MTI 0x0100 -> CAN 19100[Alias]
            val msg = ":X19100${NODE_ALIAS}N${nodeId};"
            GridConnectNetwork.sendMessage(msg)
            println("Sent Initialization Complete: $msg")
        } catch (e: Exception) {
            println("Failed to send Initialization Complete: ${e.message}")
        }
    }

    private fun sendVerifiedNodeId() {
        try {
            val nodeId = getCleanNodeId()
            // Verified Node ID: MTI 0x0170 -> CAN 19170[Alias]
            val msg = ":X19170${NODE_ALIAS}N${nodeId};"
            GridConnectNetwork.sendMessage(msg)
            println("Sent Verified Node ID: $msg")
        } catch (e: Exception) {
            println("Failed to send Verified Node ID: ${e.message}")
        }
    }

    private fun sendSimpleNodeInfoReply(destAlias: String) {
        try {
            // Build the SNIP payload bytes
            val payload = mutableListOf<Byte>()
            payload.add(1) // Version 1
            payload.addAll("Edranor".encodeToByteArray().toList())
            payload.add(0)
            payload.addAll("Lever Frame".encodeToByteArray().toList())
            payload.add(0)
            payload.addAll("1.0".encodeToByteArray().toList())
            payload.add(0)
            payload.addAll("1.2.0".encodeToByteArray().toList())
            payload.add(0)
            payload.add(1) // User Data Version 1
            payload.addAll(ConfigManager.currentConfig.node_name.encodeToByteArray().toList())
            payload.add(0)
            payload.addAll("Desktop Lever Frame Node".encodeToByteArray().toList())
            payload.add(0)

            // Simple Node Info Reply is a Global message (MTI 0x0A08).
            // Actually, SNIP is an Addressed Message.
            // Over CAN, addressed messages must include the 12-bit destination alias in the first 2 bytes of the payload.
            // Therefore, we split the payload into 6-byte chunks, and prepend the destination alias.
            val chunks = payload.chunked(6)
            for (chunk in chunks) {
                val hexData = StringBuilder()
                hexData.append("0").append(destAlias)
                for (b in chunk) {
                    hexData.append(b.toUByte().toString(16).padStart(2, '0').uppercase())
                }
                
                // MTI 0x0A08 -> CAN 19A08[Alias]
                val msg = ":X19A08${NODE_ALIAS}N${hexData};"
                GridConnectNetwork.sendMessage(msg)
            }
            println("Sent SNIP Reply")
        } catch (e: Exception) {
            println("Failed to send SNIP Reply: ${e.message}")
        }
    }

    private fun sendAllProducerIdentified() {
        try {
            val parsedTabs = ConfigManager.parseConfig(ConfigManager.toJsonString())
            parsedTabs.forEach { (_, tabDef) ->
                tabDef.levers.forEach { lever ->
                    if (lever.lcc_enabled) {
                        if (lever.lcc_event_normal.isNotBlank()) {
                            sendProducerIdentified(lever.lcc_event_normal)
                        }
                        if (lever.lcc_event_reversed.isNotBlank()) {
                            sendProducerIdentified(lever.lcc_event_reversed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error generating Producer Identified messages: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun parseEventId(eventIdStr: String): String {
        val parts = eventIdStr.split(".")
        if (parts.size in 2..7) {
            val paddedParts = parts.toMutableList()
            while (paddedParts.size < 8) {
                if (paddedParts.size >= 6) {
                    paddedParts.add(6, "00")
                } else {
                    paddedParts.add("00")
                }
            }
            return paddedParts.joinToString("") { it.padStart(2, '0') }.uppercase()
        }
        return eventIdStr.replace(".", "").padEnd(16, '0').uppercase()
    }

    private fun sendProducerIdentified(eventIdStr: String) {
        try {
            val cleanHex = parseEventId(eventIdStr)
            if (cleanHex.length == 16) {
                // Producer Identified Valid CAN MTI is 0x0544 -> 19544 prefix
                val msg = ":X19544${NODE_ALIAS}N$cleanHex;"
                GridConnectNetwork.sendMessage(msg)
                println("Sent Producer Identified: $msg")
            }
        } catch (e: Exception) {
            println("Failed to send Producer Identified for $eventIdStr: ${e.message}")
        }
    }

    override fun identifyProducer(eventIdStr: String) {
        if (eventIdStr.isBlank()) return
        try {
            val cleanHex = parseEventId(eventIdStr)
            if (cleanHex.length == 16) {
                // Identify Producer CAN MTI is 0x0914 -> 19914 prefix
                val msg = ":X19914${NODE_ALIAS}N$cleanHex;"
                GridConnectNetwork.sendMessage(msg)
                println("Sent Identify Producer: $msg")
            }
        } catch (e: Exception) {
            println("Failed to send Identify Producer for $eventIdStr: ${e.message}")
        }
    }

    override fun produceEvent(eventIdStr: String) {
        if (eventIdStr.isBlank()) return
        
        try {
            // Parse event ID intelligently to handle 7-byte inputs
            val cleanHex = parseEventId(eventIdStr)
            if (cleanHex.length == 16) {
                // PCER MTI is 0x05B4
                // GridConnect header for OpenLCB PCER with priority 1 is: 195B4
                // Plus the 12-bit alias (e.g. 12A) -> 195B412A
                val gridConnectMsg = ":X195B4${NODE_ALIAS}N$cleanHex;"
                GridConnectNetwork.sendMessage(gridConnectMsg)
                println("Sent LCC Event: $gridConnectMsg")
            }
        } catch (e: Exception) {
            println("Failed to produce event $eventIdStr: ${e.message}")
        }
    }
}
