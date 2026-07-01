package org.example.project

import android.os.Build

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
    // For Android, ideally use Context to get filesDir, but context is not available here.
    // Placeholder implementation or require initialization with context.
}

actual suspend fun loadConfigFromFile(): String? {
    // Placeholder for Android
    return null
}

actual suspend fun saveLeverStatesToFile(json: String) {
    // Placeholder
}

actual suspend fun loadLeverStatesFromFile(): String? {
    return null
}