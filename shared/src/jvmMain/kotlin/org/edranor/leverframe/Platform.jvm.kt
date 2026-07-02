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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun getLocalIpAddress(): String {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            // Skip loopback and inactive interfaces
            if (networkInterface.isLoopback || !networkInterface.isUp) continue
            
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                    return address.hostAddress ?: "Unknown"
                }
            }
        }
    } catch (e: Exception) {
        // ignore
    }
    return "Unknown"
}

actual suspend fun saveConfigToFile(json: String) = withContext(Dispatchers.IO) {
    try {
        java.io.File("leverframe_config.json").writeText(json)
    } catch (e: Exception) {
        println("Failed to save config: ${e.message}")
    }
}

actual suspend fun loadConfigFromFile(): String? = withContext(Dispatchers.IO) {
    return@withContext try {
        val file = java.io.File("leverframe_config.json")
        if (file.exists()) file.readText() else null
    } catch (e: Exception) {
        println("Failed to load config: ${e.message}")
        null
    }
}

actual suspend fun saveLeverStatesToFile(json: String) = withContext(Dispatchers.IO) {
    try {
        java.io.File("leverframe_states.json").writeText(json)
    } catch (e: Exception) {
        println("Failed to save states: ${e.message}")
    }
}

actual suspend fun loadLeverStatesFromFile(): String? = withContext(Dispatchers.IO) {
    return@withContext try {
        val file = java.io.File("leverframe_states.json")
        if (file.exists()) file.readText() else null
    } catch (e: Exception) {
        println("Failed to load states: ${e.message}")
        null
    }
}

@Composable
actual fun KeepScreenOn(keepOn: Boolean) {
    // Desktop power management is complex and requires OS-level native bindings 
    // (like JNA). If you don't need this on Desktop, leave it as a no-op.
}