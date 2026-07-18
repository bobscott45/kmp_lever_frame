plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

tasks.register("syncIosVersions") {
    group = "versioning"
    description = "Syncs versions from libs.versions.toml to iOS Config.xcconfig"
    
    val configFile = file("iosApp/Configuration/Config.xcconfig")
    val appVersion = libs.versions.app.version.get()
    val appVersionCode = libs.versions.app.versionCode.get()
    
    inputs.property("version", appVersion)
    inputs.property("versionCode", appVersionCode)
    outputs.file(configFile)
    
    doLast {
        if (configFile.exists()) {
            var content = configFile.readText()
            
            // Replace the existing version lines with the new ones from the TOML
            content = content.replace(Regex("CURRENT_PROJECT_VERSION=.*"), "CURRENT_PROJECT_VERSION=$appVersionCode")
            content = content.replace(Regex("MARKETING_VERSION=.*"), "MARKETING_VERSION=$appVersion")
            
            configFile.writeText(content)
            println("✅ Synced iOS Versions: Marketing=$appVersion, Build=$appVersionCode")
        } else {
            println("⚠️ Warning: Could not find iosApp/Configuration/Config.xcconfig")
        }
    }
}