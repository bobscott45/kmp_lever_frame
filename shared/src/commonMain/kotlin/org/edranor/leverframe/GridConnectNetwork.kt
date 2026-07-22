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

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object GridConnectNetwork {

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _connectionErrors = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val connectionErrors: SharedFlow<String> = _connectionErrors.asSharedFlow()

    private var activeSocket: Socket? = null
    private var activeServerSocket: ServerSocket? = null
    private var writeChannel: ByteWriteChannel? = null
    private var readChannel: ByteReadChannel? = null

    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var writerJob: Job? = null

    private val selectorManager = SelectorManager(Dispatchers.IO)
    private val sendQueue = Channel<String>(Channel.UNLIMITED)
    
    var onClientConnected: (() -> Unit)? = null

    /**
     * Starts listening as a TCP Server on port 12021 (Standard OpenLCB GridConnect Port).
     * Stops any existing server or client.
     */
    fun startServer(port: Int = 12021) {
        stop()
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                activeServerSocket = aSocket(selectorManager).tcp().bind(port = port)
                val serverSocket = activeServerSocket!!
                _connectionStatus.value = "Listening on port $port"
                println("GridConnect TCP Server listening on port ${serverSocket.localAddress}")

                while (isActive) {
                    _connectionStatus.value = "Listening on port $port"
                    val socket = serverSocket.accept()
                    val remoteStr = socket.remoteAddress.toString().replace("/", "")
                    _connectionStatus.value = "Connected Client: $remoteStr"
                    println("GridConnect Client connected from: $remoteStr")
                    handleSocketConnection(socket)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    val msg = "Server Error: ${e.message}"
                    println("GridConnect $msg")
                }
            }
        }
    }

    /**
     * Connects as a TCP Client to a Hub (e.g., JMRI) on port 12021.
     * Stops any existing server or client.
     */
    fun startClient(host: String, port: Int = 12021) {
        stop()
        clientJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    _connectionStatus.value = "Connecting to $host:$port..."
                    println("GridConnect Client connecting to $host:$port...")
                    val socket = aSocket(selectorManager).tcp().connect(host, port)
                    val remoteStr = socket.remoteAddress.toString().replace("/", "")
                    _connectionStatus.value = "Connected to Hub: $remoteStr"
                    println("GridConnect Client connected to Hub at: $remoteStr")
                    handleSocketConnection(socket)
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        val msg = "Connection Error: ${e.message}"
                        _connectionStatus.value = msg
                        println("GridConnect Client $msg. Retrying in 5s...")
                    }
                    delay(5000)
                }
            }
        }
    }

    private suspend fun handleSocketConnection(socket: Socket) {
        activeSocket = socket
        readChannel = socket.openReadChannel()
        writeChannel = socket.openWriteChannel(autoFlush = true)

        writerJob?.cancel()
        writerJob = CoroutineScope(Dispatchers.IO).launch {
            for (msg in sendQueue) {
                if (!isActive) break
                try {
                    val finalMsg = if (msg.endsWith("\n")) msg else "$msg\n"
                    val bytes = finalMsg.encodeToByteArray()
                    writeChannel?.writeFully(bytes)
                    writeChannel?.flush()
                    println("GridConnect actually pushed ${bytes.size} bytes: $finalMsg")
                } catch (e: Exception) {
                    val msg = "Failed to send queued message: ${e.message}"
                    println(msg)
                    _connectionErrors.tryEmit(msg)
                    break
                }
            }
        }
        
        onClientConnected?.invoke()

        try {
            var currentMessage = StringBuilder()
            
            // Read loop
            while (socket.isActive) {
                val byte = readChannel?.readByte() ?: break
                val char = byte.toInt().toChar()
                
                if (char == ':') {
                    currentMessage.clear()
                    currentMessage.append(char)
                } else if (char == ';') {
                    currentMessage.append(char)
                    val msg = currentMessage.toString()
                    _incomingMessages.tryEmit(msg)
                    currentMessage.clear()
                } else {
                    currentMessage.append(char)
                }
            }
        } catch (e: Exception) {
            val msg = "Disconnected: ${e.message}"
            _connectionStatus.value = "Disconnected"
            println("GridConnect Connection $msg")
        } finally {
            withContext(NonCancellable) {
                closeClientConnection()
                _connectionStatus.value = "Disconnected"
            }
        }
    }

    /**
     * Sends a GridConnect message (e.g. ":X195B4000N;") to the connected socket.
     */
    fun sendMessage(msg: String) {
        if (writeChannel != null && writeChannel?.isClosedForWrite == false) {
            sendQueue.trySend(msg)
        } else {
            val errMsg = "GridConnect Not Connected. Cannot send: $msg"
            println(errMsg)
        }
    }

    private fun closeClientConnection() {
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        activeSocket = null
        writeChannel = null
        readChannel = null
    }

    private fun closeAll() {
        closeClientConnection()
        try {
            activeServerSocket?.close()
        } catch (e: Exception) {}
        activeServerSocket = null
    }

    fun stop() {
        serverJob?.cancel()
        clientJob?.cancel()
        writerJob?.cancel()
        serverJob = null
        clientJob = null
        closeAll()
        _connectionStatus.value = "Disconnected"
    }
}
