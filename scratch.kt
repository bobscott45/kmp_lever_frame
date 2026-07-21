import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class JsonTab(
    val name: String,
    val show_lever_numbers: Boolean = true
)

fun main() {
    val jsonFormat = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    val json = """{"name": "test"}"""
    val tab = jsonFormat.decodeFromString<JsonTab>(json)
    println("show_lever_numbers: ${tab.show_lever_numbers}")
}
