package com.bragbuddy.app.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Room type converters for enums and `List<String>` (stored as a JSON string). */
class Converters {
    @TypeConverter fun sourceToString(v: EntrySource): String = v.name
    @TypeConverter fun stringToSource(v: String): EntrySource = EntrySource.valueOf(v)

    @TypeConverter fun statusToString(v: EntryStatus): String = v.name
    @TypeConverter fun stringToStatus(v: String): EntryStatus = EntryStatus.valueOf(v)

    @TypeConverter
    fun stringListToJson(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
