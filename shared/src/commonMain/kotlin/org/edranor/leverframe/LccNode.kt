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
                if (msg.contains("X18A70")) { // Verify Node ID (Global)
                    // Respond with Verified Node ID
                    sendVerifiedNodeId()
                } else if (msg.contains("19DE8")) { // Simple Node Info Request
                    val startIdx = msg.indexOf("19DE8")
                    if (startIdx >= 0 && msg.length >= startIdx + 8) {
                        val jmriAliasStr = msg.substring(startIdx + 5, startIdx + 8)
                        sendSimpleNodeInfoReply(jmriAliasStr)
                    }
                } else if (msg.contains("195B4")) { // PCER Event
                    val startIdx = msg.indexOf("195B4")
                    val nIdx = msg.indexOf("N", startIdx)
                    if (nIdx != -1 && msg.length >= nIdx + 17) {
                        val hexData = msg.substring(nIdx + 1, nIdx + 17)
                        _externalEvents.tryEmit(hexData)
                    }
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

    private fun sendSimpleNodeInfoReply(destAliasHex: String) {
        try {
            val destAlias = destAliasHex.toIntOrNull(16) ?: return
            
            // Build the SNIP payload bytes
            val payload = mutableListOf<Byte>()
            payload.add(4) // Version 4
            payload.addAll("Kotlin App".encodeToByteArray().toList())
            payload.add(0)
            payload.addAll("Lever Frame".encodeToByteArray().toList())
            payload.add(0)
            payload.addAll("1.0".encodeToByteArray().toList())
            payload.add(0)
            payload.addAll("1.0.0".encodeToByteArray().toList())
            payload.add(0)
            payload.add(2) // Version 2
            payload.addAll(ConfigManager.currentConfig.node_name.encodeToByteArray().toList())
            payload.add(0)
            payload.addAll("Desktop Lever Frame Node".encodeToByteArray().toList())
            payload.add(0)

            // Send in chunks of 6 bytes (since 2 bytes are used for dest alias)
            val destByte0 = ((destAlias shr 8) and 0x0F)
            val destByte1 = (destAlias and 0xFF).toByte()

            val chunks = payload.chunked(6)
            for ((index, chunk) in chunks.withIndex()) {
                val frameFlag = when {
                    chunks.size == 1 -> 0x00 // Only frame
                    index == 0 -> 0x10       // First frame
                    index == chunks.size - 1 -> 0x20 // Last frame
                    else -> 0x30             // Middle frame
                }
                val currentDestByte0 = (destByte0 or frameFlag).toByte()

                val hexData = StringBuilder()
                hexData.append(currentDestByte0.toUByte().toString(16).padStart(2, '0').uppercase())
                hexData.append(destByte1.toUByte().toString(16).padStart(2, '0').uppercase())
                for (b in chunk) {
                    hexData.append(b.toUByte().toString(16).padStart(2, '0').uppercase())
                }
                
                // MTI 0x0A08 -> CAN 19A08[Alias]
                val msg = ":X19A08${NODE_ALIAS}N${hexData};"
                GridConnectNetwork.sendMessage(msg)
            }
            println("Sent SNIP Reply to $destAliasHex")
        } catch (e: Exception) {
            println("Failed to send SNIP Reply: ${e.message}")
        }
    }

    private fun sendAllProducerIdentified() {
        ConfigManager.currentConfig.tabs.forEach { tab ->
            tab.levers.forEach { lever ->
                if (lever.lcc_event_normal.isNotBlank()) {
                    sendProducerIdentified(lever.lcc_event_normal)
                }
                if (lever.lcc_event_reversed.isNotBlank()) {
                    sendProducerIdentified(lever.lcc_event_reversed)
                }
            }
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
                // Producer Identified CAN MTI is 0x054A -> 1954A prefix
                val msg = ":X1954A${NODE_ALIAS}N$cleanHex;"
                GridConnectNetwork.sendMessage(msg)
                println("Sent Producer Identified: $msg")
            }
        } catch (e: Exception) {
            println("Failed to send Producer Identified for $eventIdStr: ${e.message}")
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
