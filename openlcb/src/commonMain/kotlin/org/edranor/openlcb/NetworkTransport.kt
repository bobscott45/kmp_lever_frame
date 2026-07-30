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

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Defines the contract for a network transport layer responsible for connecting to a hub,
 * sending and receiving messages, and managing connection status.
 */
interface NetworkTransport {
    val incomingMessages: SharedFlow<String>
    val connectionStatus: StateFlow<String>
    val connectionErrors: SharedFlow<String>

    /**
     * Called when the client connects.
     */
    var onClientConnected: (() -> Unit)?

    /**
     * Connects to a specified hub using the provided IP address.
     *
     * @param hubIp The IP address (and optionally the port) of the hub to connect to.
     */
    fun connect(hubIp: String)
    
    /**
     * Disconnects the current network connection.
     */
    fun disconnect()
    
    /**
     * Sends a message through the network transport.
     *
     * @param msg The message string to be sent.
     */
    fun sendMessage(msg: String)
}
