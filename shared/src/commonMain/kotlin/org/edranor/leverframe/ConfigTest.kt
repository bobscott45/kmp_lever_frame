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
package org.edranor.leverframe

fun main() {
    val testConfig = JsonConfig(
        node_id = "05.01.01.01.03.01",
        tabs = listOf(
            JsonTab(
                name = "Test",
                levers = listOf(
                    JsonLever(
                        lcc_event_normal = "05.01.01.01.03.01.00.01"
                    )
                )
            )
        )
    )
    
    val str = ConfigManager.jsonFormat.encodeToString(JsonConfig.serializer(), testConfig)
    println("SERIALIZED: $str")
    val parsed = ConfigManager.jsonFormat.decodeFromString<JsonConfig>(str)
    println("PARSED: $parsed")
}
