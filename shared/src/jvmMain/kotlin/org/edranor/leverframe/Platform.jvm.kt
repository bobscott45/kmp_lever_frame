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