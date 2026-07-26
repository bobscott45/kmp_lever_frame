package org.edranor.openlcb

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface NetworkTransport {
    val incomingMessages: SharedFlow<String>
    val connectionStatus: StateFlow<String>
    val connectionErrors: SharedFlow<String>

    /**
     * Called when the client connects.
     */
    var onClientConnected: (() -> Unit)?

    fun connect(hubIp: String)
    fun disconnect()
    fun sendMessage(msg: String)
}
