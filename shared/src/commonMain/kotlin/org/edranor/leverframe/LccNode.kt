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
    
    internal val cdiXml = """<?xml version="1.0" encoding="utf-8"?>
<cdi xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://openlcb.org/schema/cdi/1/1/cdi.xsd">
<identification>
<manufacturer>Edranor</manufacturer>
<model>LeverFrame Node</model>
<hardwareVersion>1.0</hardwareVersion>
<softwareVersion>1.2.0-dev</softwareVersion>
</identification>
<segment space="253" origin="0">
<group>
<name>LeverFrame Configuration</name>
<description>This node's complex interlocking rules must be configured via its native UI.</description>
</group>
</segment>
</cdi>""".encodeToByteArray()
    
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
                } else if (msg.startsWith(":X1A${NODE_ALIAS}") || 
                           msg.startsWith(":X1C${NODE_ALIAS}") || 
                           msg.startsWith(":X1D${NODE_ALIAS}") || 
                           msg.startsWith(":X1E${NODE_ALIAS}")) {
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
            val frameType = msg.substring(2, 4) // 1A, 1C, 1D, 1E
            val destAlias = msg.substring(4, 7)
            val sourceAlias = msg.substring(7, 10)
            val dataIdx = msg.indexOf('N')
            val hexData = if (dataIdx != -1 && msg.endsWith(";")) {
                msg.substring(dataIdx + 1, msg.length - 1)
            } else ""

            val bytes = hexData.chunked(2).map { it.toInt(16).toByte() }

            if (frameType == "1A") { // Single frame
                processDatagram(sourceAlias, bytes)
            } else if (frameType == "1C") { // First frame
                datagramBuffers[sourceAlias] = bytes.toMutableList()
            } else if (frameType == "1D") { // Middle frame
                datagramBuffers[sourceAlias]?.addAll(bytes)
            } else if (frameType == "1E") { // Last frame
                datagramBuffers[sourceAlias]?.let {
                    it.addAll(bytes)
                    processDatagram(sourceAlias, it.toList())
                    datagramBuffers.remove(sourceAlias)
                }
            }
        } catch (e: Exception) {
            println("Error parsing datagram frame: ${e.message}")
        }
    }

    internal fun processDatagram(sourceAlias: String, payload: List<Byte>) {
        println("Received complete datagram from $sourceAlias, length ${payload.size}")
        
        // 1. Acknowledge at the transport layer
        sendDatagramReceivedOk(sourceAlias)
        
        // 2. Process Memory Configuration Protocol (Protocol ID 0x20)
        if (payload.isNotEmpty() && payload[0].toInt() == 0x20) {
            // Check for Memory Space Read (0x40) in space 0xFF (0x03) -> 0x43
            if (payload.size >= 7 && (payload[1].toInt() and 0xFF) == 0x43) {
                val address = ((payload[2].toInt() and 0xFF).toLong() shl 24) or
                              ((payload[3].toInt() and 0xFF).toLong() shl 16) or
                              ((payload[4].toInt() and 0xFF).toLong() shl 8) or
                              ((payload[5].toInt() and 0xFF).toLong())
                val len = payload[6].toInt() and 0xFF
                
                val dataChunk = if (address < cdiXml.size) {
                    val endAddr = minOf(address + len, cdiXml.size.toLong()).toInt()
                    cdiXml.sliceArray(address.toInt() until endAddr)
                } else {
                    ByteArray(0)
                }
                
                val replyPayload = mutableListOf<Byte>()
                replyPayload.add(0x20.toByte())
                replyPayload.add(0x53.toByte()) // Read Reply (0x50) for 0xFF (0x03)
                replyPayload.add(payload[2])
                replyPayload.add(payload[3])
                replyPayload.add(payload[4])
                replyPayload.add(payload[5])
                replyPayload.addAll(dataChunk.toList())
                
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
                        GridConnectNetwork.sendMessage(":X1C${destAlias}${NODE_ALIAS}N${hexData};")
                    } else if (index == chunks.lastIndex) {
                        GridConnectNetwork.sendMessage(":X1E${destAlias}${NODE_ALIAS}N${hexData};")
                    } else {
                        GridConnectNetwork.sendMessage(":X1D${destAlias}${NODE_ALIAS}N${hexData};")
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
