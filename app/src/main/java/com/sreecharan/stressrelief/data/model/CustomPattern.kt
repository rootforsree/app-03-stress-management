package com.sreecharan.stressrelief.data.model

import kotlinx.serialization.Serializable

/**
 * Custom breathing pattern (Builder 3).
 *
 * The @Serializable annotation is JSON metadata only: the shape matches the
 * builder contract verbatim. Patterns persist as a JSON list in DataStore
 * through [com.sreecharan.stressrelief.data.local.JsonCodec].
 */
@Serializable
data class CustomPattern(
    val id: String, // "custom-<uuid>"
    val name: String, // user-given, max 40 chars
    val inhaleSec: Int, // 1..30
    val holdSec: Int, // 0..30
    val exhaleSec: Int, // 1..30
    val holdAfterSec: Int, // 0..30
    val rounds: Int, // 1..30
)
