package com.sreecharan.stressrelief.data.local

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * JSON list encoding for the *_json columns. Decoding never throws:
 * a corrupt column reads back as an empty list.
 */
internal object JsonCodec {
    private val json = Json { encodeDefaults = true }

    fun encodeStrings(values: List<String>): String = json.encodeToString(ListSerializer(String.serializer()), values)

    fun decodeStrings(raw: String): List<String> =
        runCatching {
            if (raw.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString(ListSerializer(String.serializer()), raw)
            }
        }.getOrDefault(emptyList())

    fun encodeInts(values: List<Int>): String = json.encodeToString(ListSerializer(Int.serializer()), values)

    fun decodeInts(raw: String): List<Int> =
        runCatching {
            if (raw.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString(ListSerializer(Int.serializer()), raw)
            }
        }.getOrDefault(emptyList())

    /**
     * Generic list encoding for @Serializable models (e.g. CustomPattern).
     * Decoding never throws: a corrupt column reads back as an empty list.
     */
    inline fun <reified T> encodeList(values: List<T>): String =
        json.encodeToString<List<T>>(values)

    inline fun <reified T> decodeList(raw: String): List<T> =
        runCatching {
            if (raw.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString<List<T>>(raw)
            }
        }.getOrDefault(emptyList())
}
