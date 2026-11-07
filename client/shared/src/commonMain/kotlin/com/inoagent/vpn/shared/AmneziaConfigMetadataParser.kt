package com.inoagent.vpn.shared

/**
 * A deliberately small, non-secret metadata parser. The Android tunnel library remains the
 * source of truth for full AmneziaWG config validation.
 */
object AmneziaConfigMetadataParser {
    @Throws(IllegalArgumentException::class)
    fun parse(fileName: String, contents: String): ParsedConfigMetadata {
        require(contents.lineSequence().any { it.trim().equals("[Interface]", ignoreCase = true) }) {
            "В конфигурации отсутствует секция [Interface]"
        }
        require(contents.lineSequence().any { it.trim().equals("[Peer]", ignoreCase = true) }) {
            "В конфигурации отсутствует секция [Peer]"
        }

        val endpoint = contents.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("Endpoint", ignoreCase = true) }
            ?.substringAfter('=', missingDelimiterValue = "")
            ?.trim()
            .orEmpty()

        require(endpoint.isNotBlank()) { "В конфигурации не указан Endpoint" }

        val cleanName = fileName.substringBeforeLast('.').ifBlank { "Inoagent" }
        return ParsedConfigMetadata(
            suggestedName = cleanName.take(15).map { char ->
                if (char.isLetterOrDigit() || char in "_=+.-") char else '-'
            }.joinToString("").ifBlank { "Inoagent" },
            endpoint = endpoint,
        )
    }
}

/** Stable class-shaped facade for Swift/Objective-C interoperability. */
class SharedConfigFacade {
    @Throws(IllegalArgumentException::class)
    fun parseMetadata(fileName: String, contents: String): ParsedConfigMetadata =
        AmneziaConfigMetadataParser.parse(fileName, contents)
}
