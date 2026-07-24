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

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AndroidAppContext {
    var applicationContext: Context? = null
}

class AndroidPlatform : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getLocalIpAddress(): String {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
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

actual suspend fun saveConfigToFile(json: String) {
    AndroidAppContext.applicationContext?.let { context ->
        withContext(Dispatchers.IO) {
            File(context.filesDir, "leverframe_config.json").writeText(json)
        }
    }
}

actual suspend fun loadConfigFromFile(): String? {
    return AndroidAppContext.applicationContext?.let { context ->
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "leverframe_config.json")
            if (file.exists()) file.readText() else null
        }
    }
}

actual suspend fun saveLeverStatesToFile(json: String) {
    AndroidAppContext.applicationContext?.let { context ->
        withContext(Dispatchers.IO) {
            File(context.filesDir, "leverframe_states.json").writeText(json)
        }
    }
}

actual suspend fun loadLeverStatesFromFile(): String? {
    return AndroidAppContext.applicationContext?.let { context ->
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "leverframe_states.json")
            if (file.exists()) file.readText() else null
        }
    }
}

actual suspend fun clearLeverStatesFile() {
    AndroidAppContext.applicationContext?.let { context ->
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "leverframe_states.json")
            if (file.exists()) file.delete()
        }
    }
}

@Composable
actual fun KeepScreenOn(keepOn: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    
    DisposableEffect(keepOn) {
        if (keepOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}