package com.joshuadiablito.mythos.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A language Mythos has a story catalog for. Brazilian and European
 * Portuguese are separate: their stories, spelling and voices differ.
 * [code] matches the iOS app and the catalog files ("fr", "pt-PT").
 */
@Serializable(with = LanguageSerializer::class)
enum class Language(val code: String) {
    FRENCH("fr"),
    SPANISH("es"),
    GERMAN("de"),
    ITALIAN("it"),
    /** Brazilian Portuguese. Its code stays "pt", as on iOS. */
    PORTUGUESE("pt"),
    PORTUGAL("pt-PT"),
    GREEK("el");

    /** The language's name in English, as shown to the learner. */
    val englishName: String
        get() = when (this) {
            FRENCH -> "French"
            SPANISH -> "Spanish"
            GERMAN -> "German"
            ITALIAN -> "Italian"
            PORTUGUESE -> "Portuguese (Brazil)"
            PORTUGAL -> "Portuguese (Portugal)"
            GREEK -> "Greek"
        }

    /** The name kaikki.org files the language under. Both Portuguese variants share one dictionary. */
    val dictionaryName: String
        get() = when (this) {
            PORTUGUESE, PORTUGAL -> "Portuguese"
            else -> englishName
        }

    /** The ISO 639-1 code, without a region: both Portuguese variants are "pt". */
    val baseCode: String get() = code.take(2)

    /** The language's name for itself. */
    val endonym: String
        get() = when (this) {
            FRENCH -> "Français"
            SPANISH -> "Español"
            GERMAN -> "Deutsch"
            ITALIAN -> "Italiano"
            PORTUGUESE -> "Português (Brasil)"
            PORTUGAL -> "Português (Portugal)"
            GREEK -> "Ελληνικά"
        }

    /** Two letters for the language's badge. */
    val monogram: String
        get() = when (this) {
            GREEK -> "Ελ"
            PORTUGUESE -> "Br"
            PORTUGAL -> "Pt"
            else -> code.replaceFirstChar { it.uppercase() }
        }

    /** Voice used to read stories aloud. */
    val voiceCode: String
        get() = when (this) {
            FRENCH -> "fr-FR"
            SPANISH -> "es-ES"
            GERMAN -> "de-DE"
            ITALIAN -> "it-IT"
            PORTUGUESE -> "pt-BR"
            PORTUGAL -> "pt-PT"
            GREEK -> "el-GR"
        }

    val locale: Locale get() = Locale.forLanguageTag(voiceCode)

    /** "Hello" in the language, for onboarding. */
    val greeting: String
        get() = when (this) {
            FRENCH -> "Bonjour"
            SPANISH -> "Hola"
            GERMAN -> "Hallo"
            ITALIAN -> "Ciao"
            PORTUGUESE, PORTUGAL -> "Olá"
            GREEK -> "Γεια σου"
        }

    /** "Good morning", "Good afternoon" or "Good evening" in the language. */
    fun greeting(at: LocalDateTime): String {
        val hour = at.hour
        val part = if (hour < 12) 0 else if (hour < 18) 1 else 2
        val greetings = when (this) {
            FRENCH -> listOf("Bonjour", "Bon après-midi", "Bonsoir")
            SPANISH -> listOf("Buenos días", "Buenas tardes", "Buenas noches")
            GERMAN -> listOf("Guten Morgen", "Guten Tag", "Guten Abend")
            ITALIAN -> listOf("Buongiorno", "Buon pomeriggio", "Buonasera")
            PORTUGUESE, PORTUGAL -> listOf("Bom dia", "Boa tarde", "Boa noite")
            GREEK -> listOf("Καλημέρα", "Καλό απόγευμα", "Καλησπέρα")
        }
        return greetings[part]
    }

    /** Today's date as the language writes it: "dimanche 25 octobre". */
    fun longDate(date: LocalDateTime): String =
        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))

    /** "Well done!" in the language. */
    val praise: String
        get() = when (this) {
            FRENCH -> "Bravo !"
            SPANISH -> "¡Muy bien!"
            GERMAN -> "Super gemacht!"
            ITALIAN -> "Bravissimo!"
            PORTUGUESE, PORTUGAL -> "Muito bem!"
            GREEK -> "Μπράβο!"
        }

    companion object {
        fun fromCode(code: String): Language? = entries.firstOrNull { it.code == code }
    }
}

object LanguageSerializer : KSerializer<Language> {
    override val descriptor = PrimitiveSerialDescriptor("Language", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Language) = encoder.encodeString(value.code)
    override fun deserialize(decoder: Decoder): Language {
        val code = decoder.decodeString()
        return Language.fromCode(code) ?: throw IllegalArgumentException("Unknown language $code")
    }
}
