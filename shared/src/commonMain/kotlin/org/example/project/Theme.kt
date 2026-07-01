package org.example.project

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
