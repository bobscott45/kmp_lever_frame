package org.example.project

actual val isFilePickerAvailable: Boolean = false

// Stub implementation for Android. 
// A production implementation requires passing the Activity context to an ActivityResultLauncher.
actual fun exportConfigurationFile(json: String): Boolean {
    println("Export not implemented natively for Android yet. Use Clipboard fallback.")
    return false
}

actual fun importConfigurationFile(onResult: (String?) -> Unit) {
    println("Import not implemented natively for Android yet.")
    onResult(null)
}
