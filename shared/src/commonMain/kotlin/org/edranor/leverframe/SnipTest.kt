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
import kotlin.text.encodeToByteArray

fun main() {
    val destAlias = 0x345
    val NODE_ALIAS = "12A"

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
    payload.addAll("My Node".encodeToByteArray().toList())
    payload.add(0)
    payload.addAll("Desktop Lever Frame Node".encodeToByteArray().toList())
    payload.add(0)

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

        val msg = ":X19A08${NODE_ALIAS}N${hexData};"
        println(msg)
    }
}
