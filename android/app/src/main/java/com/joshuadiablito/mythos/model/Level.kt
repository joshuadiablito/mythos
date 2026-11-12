package com.joshuadiablito.mythos.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A CEFR level, from A1 (beginner) to C2 (proficient). */
@Serializable(with = LevelSerializer::class)
enum class Level(val code: String) {
    A1("A1"), A2("A2"), B1("B1"), B2("B2"), C1("C1"), C2("C2");

    /** Every level has a catalog in every language. */
    val isAvailable: Boolean get() = true

    val title: String
        get() = when (this) {
            A1 -> "Beginner"
            A2 -> "Elementary"
            B1 -> "Intermediate"
            B2 -> "Upper intermediate"
            C1 -> "Advanced"
            C2 -> "Proficient"
        }

    val summary: String
        get() = when (this) {
            A1 -> "Everyday words, the present tense, short sentences."
            A2 -> "Past and future, routines, small stories about people."
            B1 -> "Longer stories, opinions, plans and what might have been."
            B2 -> "Arguments, reported speech and the subjunctive in earnest."
            C1 -> "Nuance, idiom and feature-length writing."
            C2 -> "Literary style, irony and rare words: as a native reads."
        }

    companion object {
        fun fromCode(code: String): Level? = entries.firstOrNull { it.code == code }
    }
}

object LevelSerializer : KSerializer<Level> {
    override val descriptor = PrimitiveSerialDescriptor("Level", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Level) = encoder.encodeString(value.code)
    override fun deserialize(decoder: Decoder): Level {
        val code = decoder.decodeString()
        return Level.fromCode(code) ?: throw IllegalArgumentException("Unknown level $code")
    }
}
