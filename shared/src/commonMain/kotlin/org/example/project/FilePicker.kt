package org.example.project

expect val isFilePickerAvailable: Boolean
expect fun exportConfigurationFile(json: String): Boolean
expect fun importConfigurationFile(onResult: (String?) -> Unit)
