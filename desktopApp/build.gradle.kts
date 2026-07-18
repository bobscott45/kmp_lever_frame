import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "org.edranor.leverframe.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LeverFrame"
            packageVersion = libs.versions.app.version.get().substringBefore("-")
            
            buildTypes.release.proguard {
                isEnabled.set(false)
            }
            
            macOS {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
            windows {
                // Windows packager requires .ico, but typically compose handles basic .png to .ico conversion or we can leave it default
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}