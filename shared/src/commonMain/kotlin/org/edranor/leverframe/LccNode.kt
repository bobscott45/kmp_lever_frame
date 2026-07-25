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
                if (msg.contains("X18A70") || msg.contains("X19A70")) { // Verify Node ID (Global)
                    // Respond with Verified Node ID
                    sendVerifiedNodeId()
                } else if (msg.contains("X18DE8") || msg.contains("X19DE8")) { // Simple Node Info Request
                    sendSimpleNodeInfoReply()
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
                }
            }
        }
    }

    private fun getCleanNodeId(): String {
        return ConfigManager.currentConfig.node_id.replace(".", "").padStart(12, '0').uppercase()
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

    private fun sendSimpleNodeInfoReply() {
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
            // Data is just streamed in 8-byte CAN frames. No dest alias or frame flags in payload.
            val chunks = payload.chunked(8)
            for (chunk in chunks) {
                val hexData = StringBuilder()
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
