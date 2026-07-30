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

/**
 * Configuration data for OpenLCB node initialization and setup.
 */
interface OpenLcbConfig {
    val nodeId: String
    val nodeName: String
    val jmriHubIp: String
}

/**
 * Handles operations related to memory spaces within the OpenLCB protocol.
 */
interface MemorySpaceHandler {
    /**
     * Retrieves the Configuration Description Information (CDI) XML payload.
     *
     * @return A byte array containing the CDI XML data.
     */
    fun getCdiXml(): ByteArray
    
    /**
     * Reads data from a specified memory space.
     *
     * @param space The identifier of the memory space to read from.
     * @return A byte array containing the data read from the memory space.
     */
    fun readMemorySpace(space: Int): ByteArray
    
    /**
     * Writes data to a specified memory space at the given address.
     *
     * @param space The identifier of the memory space to write to.
     * @param address The 64-bit address within the memory space to start writing at.
     * @param data The byte array containing the data to be written.
     */
    fun writeMemorySpace(space: Int, address: Long, data: ByteArray)
}

/**
 * Provides a list of event IDs that the node can produce.
 */
interface EventProducerProvider {
    /**
     * Retrieves the list of event IDs produced by this node.
     *
     * @return A list of produced event ID strings.
     */
    fun getProducedEvents(): List<String>
}
