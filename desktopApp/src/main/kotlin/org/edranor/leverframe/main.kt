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
 * Desktop (JVM) entry point for LeverFrame.
 * Configures the Compose Window, manages UI scaling arguments, and handles
 * environment-specific behaviors (e.g., fullscreen on Raspberry Pi).
 */
package org.edranor.leverframe

import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

import androidx.compose.ui.res.painterResource

fun main(args: Array<String>) = application {
    var runtimeUiScale = 1.0f
    for (i in args.indices) {
        if (args[i] == "--ui-scale" && i + 1 < args.size) {
            runtimeUiScale = args[i + 1].toFloatOrNull() ?: 1.0f
        }
    }
    val isRaspberryPi = System.getProperty("os.name").contains("Linux", ignoreCase = true) &&
            (System.getProperty("os.arch").contains("arm", ignoreCase = true) || System.getProperty("os.arch").contains("aarch64", ignoreCase = true))

    val windowState = if (isRaspberryPi) {
        rememberWindowState(placement = WindowPlacement.Fullscreen)
    } else {
        rememberWindowState(width = 1000.dp, height = 700.dp)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "LeverFrame",
        state = windowState,
        icon = painterResource("icon.png"),
        onKeyEvent = {
            if (isRaspberryPi && it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                exitApplication()
                true
            } else {
                false
            }
        }
    ) {
        App(runtimeUiScale = runtimeUiScale)
    }
}