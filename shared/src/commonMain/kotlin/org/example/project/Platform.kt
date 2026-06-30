package org.example.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun getLocalIpAddress(): String

expect fun saveConfigToFile(json: String)

expect fun loadConfigFromFile(): String?

expect fun saveLeverStatesToFile(json: String)

expect fun loadLeverStatesFromFile(): String?