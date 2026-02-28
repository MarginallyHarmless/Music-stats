package com.musicstats.app.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val listStringSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromStringList(list: List<String>): String = Json.encodeToString(listStringSerializer, list)

    @TypeConverter
    fun toStringList(json: String): List<String> =
        runCatching { Json.decodeFromString(listStringSerializer, json) }.getOrDefault(emptyList())
}
