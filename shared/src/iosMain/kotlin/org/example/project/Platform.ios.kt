package org.example.project

import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getLocalIpAddress(): String {
    // For iOS, returning a placeholder as fetching IP requires POSIX headers which 
    // are tricky to expose cleanly without complex cinterop or specific network frameworks.
    return "Unknown (iOS)"
}

actual suspend fun saveConfigToFile(json: String) {
    // Placeholder for iOS
}

actual suspend fun loadConfigFromFile(): String? {
    // Placeholder for iOS
    return null
}

actual suspend fun saveLeverStatesToFile(json: String) {
    // Placeholder
}

actual suspend fun loadLeverStatesFromFile(): String? {
    return null
}