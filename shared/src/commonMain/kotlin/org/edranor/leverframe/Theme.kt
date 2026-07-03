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

import androidx.compose.ui.graphics.Color

object LeverFrameTheme {
    object Colors {
        val Brass = Color(0xFFd4af37)
        val PaleBlue = Color(0xFFa2b4c7)
        
        // Lever Types
        val HomeSignal = Color(0xFF8f2727)
        val DistantSignal = Color(0xFFb29c12)
        val Points = Color(0xFF000000)
        val FacingPoints = Color(0xFF2b58b5)
        val Brown = Color(0xFF5C4033)
        val Green = Color(0xFF228B22)
        val Spare = Color(0xFFb8b8b8)
        
        // Backgrounds & Surfaces
        val NetworkErrorBg = Color(0xFF3b1a1a)
        val NetworkErrorText = Color(0xFFffb3b3)
        val DarkSurface = Color(0xFF1E1E1E)
        val DarkSurfaceLighter = Color(0xFF2A2A2A)
        val FrameGradientTop = Color(0xFF000000)
        val FrameGradientMid = Color(0xFF1a1a1a)
        val FrameGradientBottom = Color(0xFF000000)
        val FrameBorder = Color(0xFF2b2b2b)
        val TabUnselected = Color.White
        
        val ErrorText = Color.Red
    }
    
    object Animation {
        const val LeverSpringStiffness = 200f
        const val LeverSpringDamping = 0.6f
        const val ShakeDurationMs = 40
        const val ShakeOffsetTarget = 8f
        const val ErrorBannerAnimationMs = 300
    }
}
