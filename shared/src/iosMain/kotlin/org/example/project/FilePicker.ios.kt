package org.example.project

actual val isFilePickerAvailable: Boolean = false

// Stub implementation for iOS.
// A production implementation requires UIDocumentPickerViewController.
actual fun exportConfigurationFile(json: String): Boolean {
    println("Export not implemented natively for iOS yet.")
    return false
}

actual fun importConfigurationFile(onResult: (String?) -> Unit) {
    println("Import not implemented natively for iOS yet.")
    onResult(null)
}
