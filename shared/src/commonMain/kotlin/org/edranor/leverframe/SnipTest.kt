/*
 * Copyright (C) 2026 Robert Scott
 *
 * This file is part of LeverFrame.
 *
 * LeverFrame is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LeverFrame is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LeverFrame.  If not, see <https://www.gnu.org/licenses/>.
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
