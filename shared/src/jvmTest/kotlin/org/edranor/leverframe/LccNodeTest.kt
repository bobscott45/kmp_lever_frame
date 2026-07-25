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

import kotlin.test.Test
import kotlin.test.assertEquals

class LccNodeTest {

    @Test
    fun testParseEventId() {
        // Full 8-byte dot notation
        assertEquals("0501010103011101", LccNode.parseEventId("05.01.01.01.03.01.11.01"))
        
        // 2-byte short notation: node ID prefix is usually 6 bytes, then 2 bytes. 
        // If provided just "11.01", it will pad the rest with "00".
        // The while loop appends "00" to the end if size < 6, so:
        // "11.01" -> "11.01.00" -> ... -> "11.01.00.00.00.00" (size 6)
        // Then size >= 6, it inserts at index 6: "00", then "00"
        // Wait, if it just appends to the end, the result is "1101000000000000"
        assertEquals("1101000000000000", LccNode.parseEventId("11.01"))
        
        // If 7 bytes provided (e.g. node ID + 1 byte), it inserts "00" at index 6
        // "01.02.03.04.05.06.07" -> inserts at 6 -> "01.02.03.04.05.06.00.07" -> "0102030405060007"
        assertEquals("0102030405060007", LccNode.parseEventId("01.02.03.04.05.06.07"))

        // Single byte or empty: falls back to padEnd(16, '0')
        assertEquals("FF00000000000000", LccNode.parseEventId("FF"))
        
        // Hex string without dots
        assertEquals("0501010103011101", LccNode.parseEventId("0501010103011101"))
    }
    @Test
    fun testDatagramAssembly() {
        // Clear any leftover buffers
        LccNode.datagramBuffers.clear()
        
        // Assume NODE_ALIAS is 12A (the default hardcoded one right now for testing)
        // A middle frame requires a first frame
        LccNode.handleIncomingDatagramFrame(":X1C12A456N01020304050607;") // First frame
        assertEquals(1, LccNode.datagramBuffers.size)
        assertEquals(7, LccNode.datagramBuffers["456"]?.size)
        
        LccNode.handleIncomingDatagramFrame(":X1D12A456N08090A0B0C0D0E;") // Middle frame
        assertEquals(14, LccNode.datagramBuffers["456"]?.size)
        
        LccNode.handleIncomingDatagramFrame(":X1E12A456N0F10;") // Last frame
        // Buffer should be cleared after last frame
        assertEquals(0, LccNode.datagramBuffers.size)
        
        // Single frame datagram
        LccNode.handleIncomingDatagramFrame(":X1A12A789N112233;") 
        // Just passes through, doesn't leave anything in buffer
        assertEquals(0, LccNode.datagramBuffers.size)
    }
    @Test
    fun testCdiXmlNullTerminator() {
        // The CDI XML byte array must end with a null terminator (0x00)
        // according to the NMRA S-9.7.4.1 standard for Configuration Description Information.
        val lastByte = LccNode.cdiXml.last()
        assertEquals(0.toByte(), lastByte, "CDI XML must be null-terminated")
    }

    @Test
    fun testMemoryConfigurationRead() {
        // Test parsing of a Memory Space Read request (0x20 0x43) for the CDI space (0xFF).
        // 0x20 = Memory Configuration Protocol
        // 0x43 = Read Request (0x40) | Space 0xFF (0x03)
        // address = 0x00 00 00 00, length = 64
        val requestPayload = listOf<Byte>(
            0x20, 0x43, 0x00, 0x00, 0x00, 0x00, 64
        )
        
        // This processDatagram is internal and won't actually throw an exception,
        // it will just call sendDatagram() and sendDatagramReceivedOk() internally.
        // We just ensure it doesn't crash or throw index out of bounds.
        LccNode.processDatagram("456", requestPayload)
        
        // Test a read that exceeds the CDI size
        val largeAddressRequest = listOf<Byte>(
            0x20, 0x43, 0x00, 0x00, 0x08, 0x00, 64 // address 0x0800 = 2048, longer than CDI
        )
        LccNode.processDatagram("456", largeAddressRequest)
    }
}
