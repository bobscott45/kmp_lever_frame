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
}
