package org.example.project

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GridConnectNetwork {

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    private var activeSocket: Socket? = null
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
                val serverSocket = aSocket(selectorManager).tcp().bind(port = port)
                println("GridConnect TCP Server listening on port ${serverSocket.localAddress}")

                while (isActive) {
                    val socket = serverSocket.accept()
                    println("GridConnect Client connected from: ${socket.remoteAddress}")
                    handleSocketConnection(socket)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    println("GridConnect Server Error: ${e.message}")
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
                    println("GridConnect Client connecting to $host:$port...")
                    val socket = aSocket(selectorManager).tcp().connect(host, port)
                    println("GridConnect Client connected to Hub at: ${socket.remoteAddress}")
                    handleSocketConnection(socket)
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        println("GridConnect Client Connection Error: ${e.message}. Retrying in 5s...")
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
                    println("Failed to send queued message: ${e.message}")
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
            println("GridConnect Connection disconnected: ${e.message}")
        } finally {
            withContext(NonCancellable) {
                closeConnection()
            }
        }
    }

    /**
     * Sends a GridConnect message (e.g. ":X195B4000N;") to the connected socket.
     */
    fun sendMessage(msg: String) {
        if (activeSocket?.isActive == true) {
            sendQueue.trySend(msg)
        } else {
            println("GridConnect Not Connected. Cannot send: $msg")
        }
    }

    private fun closeConnection() {
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        activeSocket = null
        writeChannel = null
        readChannel = null
    }

    fun stop() {
        serverJob?.cancel()
        clientJob?.cancel()
        writerJob?.cancel()
        serverJob = null
        clientJob = null
        closeConnection()
    }
}
