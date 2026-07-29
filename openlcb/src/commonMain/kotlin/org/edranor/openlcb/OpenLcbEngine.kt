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
package org.edranor.openlcb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

object OpenLcbEngine : LccNetworkClient {

    private var NODE_ALIAS = "12A" // Using a fixed alias for simplicity
    private var lccJob: Job? = null
    internal val datagramBuffers = mutableMapOf<String, MutableList<Byte>>()
    
    private var config: OpenLcbConfig? = null
    private var memoryHandler: MemorySpaceHandler? = null
    private var eventProvider: EventProducerProvider? = null

    private var transport: NetworkTransport? = null

    fun configure(
        config: OpenLcbConfig,
        memoryHandler: MemorySpaceHandler,
        eventProvider: EventProducerProvider,
        transport: NetworkTransport = GridConnectNetwork
    ) {
        this.config = config
        this.memoryHandler = memoryHandler
        this.eventProvider = eventProvider
        this.transport = transport
    }

    private val _externalEvents = MutableSharedFlow<String>(extraBufferCapacity = 100)
    override val externalEvents = _externalEvents.asSharedFlow()

    private val _connectionStatus = kotlinx.coroutines.flow.MutableStateFlow("Disconnected")
    override val connectionStatus = _connectionStatus.asStateFlow()

    private val _connectionErrors = MutableSharedFlow<String>(extraBufferCapacity = 10)
    override val connectionErrors = _connectionErrors.asSharedFlow()

    override fun disconnect() {
        lccJob?.cancel()
        transport?.disconnect()
    }

    override fun initialize() {
        val conf = config ?: return
        val hubIp = conf.jmriHubIp.trim()
        
        // Generate pseudo-random alias to avoid JMRI collisions
        NODE_ALIAS = kotlin.random.Random.nextInt(1, 4096).toString(16).padStart(3, '0').uppercase()

        val tr = transport ?: GridConnectNetwork
        
        tr.onClientConnected = {
            CoroutineScope(Dispatchers.Default).launch {
                kotlinx.coroutines.delay(500) // Give network time to settle
                sendAliasMapDefinition()
                sendInitializationComplete()
                sendAllProducerIdentified()
            }
        }

        tr.connect(hubIp)

        CoroutineScope(Dispatchers.Default).launch {
            tr.connectionStatus.collect { _connectionStatus.value = it }
        }
        CoroutineScope(Dispatchers.Default).launch {
            tr.connectionErrors.collect { _connectionErrors.emit(it) }
        }
        
        lccJob?.cancel()
        lccJob = CoroutineScope(Dispatchers.Default).launch {
            tr.incomingMessages.collect { msgRaw ->
                val msg = msgRaw.uppercase()
                if (msg.contains("X18490") || msg.contains("X19490")) { // Verify Node ID (Global)
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
                } else if (msg.contains("X18970") || msg.contains("X19970") || msg.contains("X18968") || msg.contains("X19968")) {
                    sendAllProducerIdentified()
                } else if (msg.contains("X18914") || msg.contains("X19914")) {
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
        return config?.nodeId?.replace(".", "")?.padStart(12, '0')?.uppercase() ?: "000000000000"
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
        
        sendDatagramReceivedOk(sourceAlias)
        
        if (payload.isNotEmpty() && payload[0].toInt() == 0x20) {
            val subCmd = payload[1].toInt() and 0xFF
            val handler = memoryHandler ?: return
            
            if ((subCmd and 0xC0) == 0x40 && payload.size >= 7) {
                val space = subCmd and 0x03
                val address = ((payload[2].toInt() and 0xFF).toLong() shl 24) or
                              ((payload[3].toInt() and 0xFF).toLong() shl 16) or
                              ((payload[4].toInt() and 0xFF).toLong() shl 8) or
                              ((payload[5].toInt() and 0xFF).toLong())
                val len = payload[6].toInt() and 0xFF
                
                val memorySpace = if (space == 0x03) handler.getCdiXml() else if (space == 0x01) handler.readMemorySpace(space) else ByteArray(0)
                
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
            else if ((subCmd and 0xC0) == 0x00 && payload.size >= 7) {
                val space = subCmd and 0x03
                val address = ((payload[2].toInt() and 0xFF).toLong() shl 24) or
                              ((payload[3].toInt() and 0xFF).toLong() shl 16) or
                              ((payload[4].toInt() and 0xFF).toLong() shl 8) or
                              ((payload[5].toInt() and 0xFF).toLong())
                              
                val writeData = payload.drop(6)
                handler.writeMemorySpace(space, address, writeData.toByteArray())
                
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
                transport?.sendMessage(":X1A${destAlias}${NODE_ALIAS}N${hexData};")
            } else {
                val chunks = payload.chunked(8)
                for ((index, chunk) in chunks.withIndex()) {
                    val hexData = chunk.joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    if (index == 0) {
                        transport?.sendMessage(":X1B${destAlias}${NODE_ALIAS}N${hexData};")
                    } else if (index == chunks.lastIndex) {
                        transport?.sendMessage(":X1D${destAlias}${NODE_ALIAS}N${hexData};")
                    } else {
                        transport?.sendMessage(":X1C${destAlias}${NODE_ALIAS}N${hexData};")
                    }
                }
            }
        } catch (e: Exception) {
            println("Failed to send datagram: ${e.message}")
        }
    }

    private fun sendDatagramReceivedOk(destAlias: String) {
        try {
            val msg = ":X19A28${NODE_ALIAS}N0${destAlias};"
            transport?.sendMessage(msg)
        } catch (e: Exception) {
            println("Failed to send Datagram Received OK: ${e.message}")
        }
    }

    private fun sendProtocolSupportReply(destAlias: String) {
        try {
            val msg = ":X19668${NODE_ALIAS}N0${destAlias}D41800000000;"
            transport?.sendMessage(msg)
        } catch (e: Exception) {
            println("Failed to send Protocol Support Reply: ${e.message}")
        }
    }

    private fun sendAliasMapDefinition() {
        try {
            val nodeId = getCleanNodeId()
            val msg = ":X10700${NODE_ALIAS}N${nodeId};"
            transport?.sendMessage(msg)
        } catch (e: Exception) {
            println("Failed to send AMD: ${e.message}")
        }
    }

    private fun sendInitializationComplete() {
        try {
            val nodeId = getCleanNodeId()
            val msg = ":X19087${NODE_ALIAS}N${nodeId};"
            transport?.sendMessage(msg)
        } catch (e: Exception) {
            println("Failed to send Initialization Complete: ${e.message}")
        }
    }

    private fun sendVerifiedNodeId() {
        try {
            val nodeId = getCleanNodeId()
            val msg = ":X19170${NODE_ALIAS}N${nodeId};"
            transport?.sendMessage(msg)
        } catch (e: Exception) {
            println("Failed to send Verified Node ID: ${e.message}")
        }
    }

    private fun sendSimpleNodeInfoReply(destAlias: String) {
        val conf = config ?: return
        try {
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
            payload.addAll(conf.nodeName.encodeToByteArray().toList())
            payload.add(0)
            payload.addAll("Desktop Lever Frame Node".encodeToByteArray().toList())
            payload.add(0)

            val chunks = payload.chunked(6)
            for (chunk in chunks) {
                val hexData = StringBuilder()
                hexData.append("0").append(destAlias)
                for (b in chunk) {
                    hexData.append(b.toUByte().toString(16).padStart(2, '0').uppercase())
                }
                val msg = ":X19A08${NODE_ALIAS}N${hexData};"
                transport?.sendMessage(msg)
            }
        } catch (e: Exception) {
            println("Failed to send SNIP Reply: ${e.message}")
        }
    }

    private fun sendAllProducerIdentified() {
        val events = eventProvider?.getProducedEvents() ?: return
        for (eventIdStr in events) {
            sendProducerIdentified(eventIdStr)
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
                val msg = ":X19544${NODE_ALIAS}N$cleanHex;"
                transport?.sendMessage(msg)
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
                val msg = ":X19914${NODE_ALIAS}N$cleanHex;"
                transport?.sendMessage(msg)
            }
        } catch (e: Exception) {
            println("Failed to send Identify Producer for $eventIdStr: ${e.message}")
        }
    }

    override fun produceEvent(eventIdStr: String) {
        if (eventIdStr.isBlank()) return
        try {
            val cleanHex = parseEventId(eventIdStr)
            if (cleanHex.length == 16) {
                val gridConnectMsg = ":X195B4${NODE_ALIAS}N$cleanHex;"
                transport?.sendMessage(gridConnectMsg)
            }
        } catch (e: Exception) {
            println("Failed to produce event $eventIdStr: ${e.message}")
        }
    }
}
