package org.example.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun getLocalIpAddress(): String

expect suspend fun saveConfigToFile(json: String)

expect suspend fun loadConfigFromFile(): String?

expect suspend fun saveLeverStatesToFile(json: String)

expect suspend fun loadLeverStatesFromFile(): String?