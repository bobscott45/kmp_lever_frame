package org.example.project

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

actual fun saveConfigToFile(json: String) {
    try {
        java.io.File("leverframe_config.json").writeText(json)
    } catch (e: Exception) {
        println("Failed to save config: ${e.message}")
    }
}

actual fun loadConfigFromFile(): String? {
    return try {
        val file = java.io.File("leverframe_config.json")
        if (file.exists()) file.readText() else null
    } catch (e: Exception) {
        println("Failed to load config: ${e.message}")
        null
    }
}