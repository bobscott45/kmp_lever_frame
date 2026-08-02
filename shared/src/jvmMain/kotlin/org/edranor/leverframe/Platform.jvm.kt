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
 * Desktop (JVM) implementation of platform-specific utilities.
 * Handles file I/O to the local working directory and retrieving the local machine's IP.
 */
package org.edranor.leverframe
import org.edranor.leverframe.*

import org.edranor.leverframe.network.*
import org.edranor.leverframe.services.*
import org.edranor.leverframe.ui.screens.main.*
import org.edranor.leverframe.ui.components.*
import org.edranor.leverframe.ui.theme.*
import org.edranor.leverframe.di.*
import org.edranor.leverframe.ui.screens.editor.*
import org.edranor.leverframe.domain.models.*
import org.edranor.leverframe.config.*
import org.edranor.leverframe.ui.screens.schematic.*
import org.edranor.leverframe.domain.engine.*
import org.edranor.leverframe.domain.parser.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable

/**
 * JVM implementation of [Platform]. Provides platform information specific to desktop environments.
 * 
 * In this architecture, it satisfies the expect declaration for JVM targets, providing 
 * the underlying Java runtime version.
 */
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

actual suspend fun clearLeverStatesFile() = withContext(Dispatchers.IO) {
    try {
        val file = java.io.File("leverframe_states.json")
        if (file.exists()) {
            file.delete()
        }
    } catch (e: Exception) {
        println("Failed to clear states: ${e.message}")
    }
}

@Composable
actual fun KeepScreenOn(keepOn: Boolean) {
    // Desktop power management is complex and requires OS-level native bindings 
    // (like JNA). If you don't need this on Desktop, leave it as a no-op.
}

actual val isAppExitAvailable: Boolean = true

actual fun exitApp() {
    kotlin.system.exitProcess(0)
}

actual val isSystemPowerControlAvailable: Boolean = System.getProperty("os.name").contains("Linux", ignoreCase = true) &&
        (System.getProperty("os.arch").contains("arm", ignoreCase = true) || System.getProperty("os.arch").contains("aarch64", ignoreCase = true))

actual fun shutdownSystem() {
    try {
        Runtime.getRuntime().exec(arrayOf("sh", "-c", "systemctl poweroff || sudo poweroff || sudo shutdown -h now"))
    } catch (e: Exception) {
        println("Failed to shutdown: ${e.message}")
    }
}