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
/**
 * Acts as the bridge between the LeverFrame domain logic and the decoupled OpenLCB network engine.
 * Implements OpenLCB configuration interfaces to provide the node identity, connection properties,
 * and the dynamic Memory Space (CDI) required for remote configuration via JMRI.
 */
package org.edranor.leverframe
import org.edranor.openlcb.*
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object LccNode : OpenLcbConfig, MemorySpaceHandler, EventProducerProvider, LccNetworkClient {

    override val nodeId: String
        get() = ConfigManager.currentConfig.node_id

    override val nodeName: String
        get() = ConfigManager.currentConfig.node_name

    override val jmriHubIp: String
        get() = ConfigManager.currentConfig.jmri_hub_ip

    override val externalEvents: SharedFlow<String>
        get() = OpenLcbEngine.externalEvents

    override val connectionStatus: StateFlow<String>
        get() = OpenLcbEngine.connectionStatus

    override val connectionErrors: SharedFlow<String>
        get() = OpenLcbEngine.connectionErrors

    override fun initialize() {
        OpenLcbEngine.configure(this, this, this)
        OpenLcbEngine.initialize()
    }

    override fun disconnect() {
        OpenLcbEngine.disconnect()
    }

    override fun produceEvent(eventIdStr: String) {
        OpenLcbEngine.produceEvent(eventIdStr)
    }

    override fun parseEventId(eventIdStr: String): String {
        return OpenLcbEngine.parseEventId(eventIdStr)
    }

    override fun identifyProducer(eventIdStr: String) {
        OpenLcbEngine.identifyProducer(eventIdStr)
    }

    override fun getProducedEvents(): List<String> {
        val events = mutableListOf<String>()
        val parsedTabs = ConfigManager.parseConfig(ConfigManager.toJsonString())
        parsedTabs.forEach { (_, tabDef) ->
            tabDef.levers.forEach { lever ->
                if (lever.lcc_enabled) {
                    if (lever.lcc_event_normal.isNotBlank()) events.add(lever.lcc_event_normal)
                    if (lever.lcc_event_reversed.isNotBlank()) events.add(lever.lcc_event_reversed)
                }
            }
        }
        return events
    }

    override fun getCdiXml(): ByteArray {
        val config = ConfigManager.currentConfig
        val tabGroups = StringBuilder()
        for (tab in config.tabs) {
            val numLevers = tab.levers.size
            val numBlocks = tab.blocks.size
            if (numLevers > 0 || numBlocks > 0) {
                tabGroups.append("""
    <group>
        <name>${tab.name}</name>
        <description>Configuration for ${tab.name}</description>""")
        
                if (numLevers > 0) {
                    tabGroups.append("""
        <group replication="$numLevers">
            <name>Levers</name>
            <repname>Lever</repname>
            <string size="${OpenLcbConstants.CDI_LABEL_SPACE}"><name>Name</name></string>
            <eventid><name>Event Normal</name></eventid>
            <eventid><name>Event Reversed</name></eventid>
        </group>""")
                }
                
                if (numBlocks > 0) {
                    tabGroups.append("""
        <group replication="$numBlocks">
            <name>Blocks</name>
            <repname>Block</repname>
            <string size="${OpenLcbConstants.CDI_LABEL_SPACE}"><name>Name</name></string>
            <string size="${OpenLcbConstants.CDI_SHORT_CODE_SPACE}"><name>Short Code</name></string>
            <eventid><name>Event Occupied</name></eventid>
            <eventid><name>Event Empty</name></eventid>
        </group>""")
                }
                
                tabGroups.append("""
    </group>""")
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

    override fun readMemorySpace(space: Int): ByteArray {
        if (space == 0x01) return buildMemorySpace()
        return ByteArray(0)
    }

    override fun writeMemorySpace(space: Int, address: Long, data: ByteArray) {
        if (space == 0x01) {
            val currentMem = buildMemorySpace()
            if (address < currentMem.size) {
                val endAddr = minOf(address + data.size, currentMem.size.toLong()).toInt()
                for (i in address.toInt() until endAddr) {
                    currentMem[i] = data[i - address.toInt()]
                }
                applyMemorySpace(currentMem)
            }
        }
    }

    private fun expandEventId(eventId: String, nodeId: String): String {
        if (eventId.isBlank()) return "".padEnd(16, '0')
        val clean = eventId.replace(".", "")
        if (clean.length == 4) {
            return (nodeId.replace(".", "") + clean).padEnd(16, '0').uppercase()
        }
        return clean.padEnd(16, '0').uppercase()
    }

    private fun contractEventId(hexEventId: String, nodeId: String): String {
        val nodeHex = nodeId.replace(".", "").uppercase()
        val hexUpper = hexEventId.uppercase()
        if (hexUpper.startsWith(nodeHex) && hexUpper.length == 16) {
            val suffix = hexUpper.substring(12, 16)
            return "${suffix.substring(0, 2)}.${suffix.substring(2, 4)}"
        }
        return hexUpper.chunked(2).joinToString(".")
    }

    private fun writeString(str: String, maxSize: Int, buf: ByteArray, off: Int) {
        val bytes = str.encodeToByteArray()
        val len = minOf(bytes.size, maxSize - 1)
        bytes.copyInto(buf, off, 0, len)
        buf[off + len] = 0
    }

    private fun readString(buf: ByteArray, off: Int, maxSize: Int): String {
        val slice = buf.sliceArray(off until off + maxSize)
        val nullIdx = slice.indexOf(0.toByte())
        val len = if (nullIdx >= 0) nullIdx else maxSize
        return slice.sliceArray(0 until len).decodeToString()
    }

    private fun buildMemorySpace(): ByteArray {
        val config = ConfigManager.currentConfig
        val numLevers = config.tabs.sumOf { it.levers.size }
        val numBlocks = config.tabs.sumOf { it.blocks.size }
        val leverSize = OpenLcbConstants.CDI_LABEL_SPACE + 16
        val blockSize = OpenLcbConstants.CDI_LABEL_SPACE + OpenLcbConstants.CDI_SHORT_CODE_SPACE + 16
        val size = 3 + numLevers * leverSize + numBlocks * blockSize
        val buffer = ByteArray(size)
        
        buffer[0] = if (config.lcc_master) 1 else 0
        buffer[1] = if (config.lcc_enabled) 1 else 0
        buffer[2] = if (config.restore_last_state) 1 else 0
        
        var offset = 3
        for (tab in config.tabs) {
            for (lever in tab.levers) {
                writeString(lever.label, OpenLcbConstants.CDI_LABEL_SPACE, buffer, offset)
                offset += OpenLcbConstants.CDI_LABEL_SPACE
                
                val normHex = expandEventId(lever.lcc_event_normal, config.node_id)
                val revHex = expandEventId(lever.lcc_event_reversed, config.node_id)
                val normBytes = normHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val revBytes = revHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                normBytes.copyInto(buffer, offset)
                revBytes.copyInto(buffer, offset + 8)
                offset += 16
            }
            for (block in tab.blocks) {
                writeString(block.label, OpenLcbConstants.CDI_LABEL_SPACE, buffer, offset)
                offset += OpenLcbConstants.CDI_LABEL_SPACE
                writeString(block.short_code, OpenLcbConstants.CDI_SHORT_CODE_SPACE, buffer, offset)
                offset += OpenLcbConstants.CDI_SHORT_CODE_SPACE
                
                val occHex = expandEventId(block.lcc_event_occupied, config.node_id)
                val empHex = expandEventId(block.lcc_event_empty, config.node_id)
                val occBytes = occHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val empBytes = empHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                occBytes.copyInto(buffer, offset)
                empBytes.copyInto(buffer, offset + 8)
                offset += 16
            }
        }
        return buffer
    }

    private fun applyMemorySpace(buffer: ByteArray) {
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
                if (offset + (OpenLcbConstants.CDI_LABEL_SPACE + 16) <= buffer.size) {
                    val label = readString(buffer, offset, OpenLcbConstants.CDI_LABEL_SPACE)
                    offset += OpenLcbConstants.CDI_LABEL_SPACE
                    
                    val normHex = buffer.copyOfRange(offset, offset + 8).joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    val revHex = buffer.copyOfRange(offset + 8, offset + 16).joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    offset += 16
                    lever.copy(
                        label = label,
                        lcc_event_normal = contractEventId(normHex, config.node_id),
                        lcc_event_reversed = contractEventId(revHex, config.node_id)
                    )
                } else {
                    lever
                }
            }
            
            val newBlocks = tab.blocks.map { block ->
                if (offset + (OpenLcbConstants.CDI_LABEL_SPACE + OpenLcbConstants.CDI_SHORT_CODE_SPACE + 16) <= buffer.size) {
                    val label = readString(buffer, offset, OpenLcbConstants.CDI_LABEL_SPACE)
                    offset += OpenLcbConstants.CDI_LABEL_SPACE
                    val shortCode = readString(buffer, offset, OpenLcbConstants.CDI_SHORT_CODE_SPACE)
                    offset += OpenLcbConstants.CDI_SHORT_CODE_SPACE
                    
                    val occHex = buffer.copyOfRange(offset, offset + 8).joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    val empHex = buffer.copyOfRange(offset + 8, offset + 16).joinToString("") { it.toUByte().toString(16).padStart(2, '0').uppercase() }
                    offset += 16
                    block.copy(
                        label = label,
                        short_code = shortCode,
                        lcc_event_occupied = contractEventId(occHex, config.node_id),
                        lcc_event_empty = contractEventId(empHex, config.node_id)
                    )
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
}
