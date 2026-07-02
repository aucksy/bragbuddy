package com.bragbuddy.app.data.ai

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

/**
 * Shared, lenient JSON handling for AI replies. Free models sometimes wrap their JSON in prose or
 * ```json fences despite `response_format`, so [extractObject] slices out the object before we
 * decode. Kept as its own object so the extraction/parse is unit-testable without a live network.
 */
object AiJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Slice the JSON object out of a model reply (first `{` … last `}`). Throws if there is none. */
    fun extractObject(text: String): String {
        val trimmed = text.trim().removePrefix("﻿").trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "No JSON object in reply" }
        return trimmed.substring(start, end + 1)
    }

    /** Extract then decode. Throws on either failure — callers fail safe to the Inbox. */
    fun <T> parse(text: String, deserializer: DeserializationStrategy<T>): T =
        json.decodeFromString(deserializer, extractObject(text))
}
