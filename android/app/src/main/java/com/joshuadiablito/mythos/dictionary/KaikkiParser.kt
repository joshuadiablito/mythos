package com.joshuadiablito.mythos.dictionary

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer

/** Parses kaikki.org's per-word JSONL files: one line per part of speech.
 *  https://kaikki.org/dictionary/rawdata.html — ported from the iOS app's KaikkiParser.swift. */
object KaikkiParser {
    fun url(language: String, word: String): String {
        val nfc = Normalizer.normalize(word, Normalizer.Form.NFC)
        // By Unicode scalar, as kaikki.org segments its paths.
        val scalars = nfc.codePoints().toArray()
        val first = String(scalars, 0, minOf(1, scalars.size))
        val firstTwo = String(scalars, 0, minOf(2, scalars.size))
        val path = listOf(language, "meaning", first, firstTwo, "$nfc.jsonl").joinToString("/") { encodeURIComponent(it) }
        return "https://kaikki.org/dictionary/$path"
    }

    /** JavaScript's encodeURIComponent, which kaikki.org's paths are built with. */
    private fun encodeURIComponent(s: String): String {
        val out = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (c < 128 && (ch.isLetterOrDigit() || ch in "-_.!~*'()")) out.append(ch)
            else out.append('%').append("%02X".format(c))
        }
        return out.toString()
    }

    /** Tags marking bookkeeping rows in wiktextract's `forms`, not real word forms. */
    private val metaFormTags = setOf(
        "table-tags", "inflection-template", "class", "romanization",
        "multiword-construction", // "avoir + past participle"
        "combined-form", // Spanish verb + clitic pronoun, e.g. "cómelo"
    )

    // Conjugation tables sometimes carry pronunciation rows that wiktextract
    // mistakes for forms (French "mɑ̃ʒ" beside "mange"). These characters occur
    // in IPA but in no orthography we support. θ, β and χ are Greek letters, so aren't listed.
    private val ipaOnly: Set<Int> = "ɑɐɒɔəɘɛɜɪʊʌʒʃʁʀŋɲɥɾʎðɣʔˈˌːɨʉɯɤɵɞɶʏʝʋɫ̃".codePoints().toArray().toSet()

    private fun hasIPA(s: String): Boolean = s.codePoints().anyMatch { it in ipaOnly }

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(jsonl: String): List<Entry> = jsonl.split("\n").filter { it.isNotEmpty() }.mapNotNull { line ->
        // A truncated or malformed line loses that part of speech, not the lookup.
        val raw = runCatching { json.parseToJsonElement(line) }.getOrNull() as? JsonObject ?: return@mapNotNull null
        parseEntry(raw)
    }

    fun parseEntry(raw: JsonObject): Entry? {
        val word = str(raw["word"]) ?: return null
        val pos = str(raw["pos"]) ?: return null
        val (senses, formOf) = parseSenses(raw["senses"])
        if (senses.isEmpty() && formOf.isEmpty()) return null

        val sounds = objects(raw["sounds"])
        val ipa = sounds.firstNotNullOfOrNull { str(it["ipa"]) }
        val audio = sounds.firstNotNullOfOrNull { str(it["mp3_url"]) ?: str(it["ogg_url"]) }
        val head = objects(raw["head_templates"]).firstNotNullOfOrNull { str(it["expansion"]) }

        return Entry(
            word = word, pos = pos, head = if (head == word) null else head, ipa = ipa, audioURL = audio,
            senses = senses, forms = parseForms(raw["forms"], word), formOf = formOf,
        )
    }

    private fun parseForms(raw: JsonElement?, word: String): List<WordForm> {
        val seen = mutableSetOf<String>()
        val forms = mutableListOf<WordForm>()
        for (f in objects(raw)) {
            val form = str(f["form"])?.trim() ?: continue
            if (form == "-" || form == "—") continue
            val tags = strings(f["tags"])
            if (tags.any { it in metaFormTags }) continue
            if (hasIPA(form) && !hasIPA(word)) continue
            val key = "$form|${tags.sorted().joinToString(",")}"
            if (!seen.add(key)) continue
            val source = f["source"]
            forms.add(WordForm(form, tags, fromTable = source != null && source !is JsonNull))
        }
        return forms
    }

    private fun parseSenses(raw: JsonElement?): Pair<List<Sense>, List<FormOf>> {
        val senses = mutableListOf<Sense>()
        val formOf = mutableListOf<FormOf>()
        for (s in objects(raw)) {
            // Nested senses list the parent gloss first; the last one is the specific meaning.
            val gloss = strings(s["glosses"]).lastOrNull() ?: continue
            val tags = strings(s["tags"])
            val lemma = objects(s["form_of"]).firstNotNullOfOrNull { str(it["word"]) }
            if (lemma != null) {
                // "…simple past of γράφω (gráfo)": drop the lemma and any romanisation after it.
                val pattern = Regex("\\s+of\\s+${Regex.escape(lemma)}(\\s*\\([^)]*\\))?$")
                formOf.add(FormOf(lemma, gloss.replace(pattern, ""), tags))
                continue
            }
            // "alternative form of ξεχνάω": the other spelling has the full entry and table.
            val alternative = objects(s["alt_of"]).firstNotNullOfOrNull { str(it["word"]) }
            if (alternative != null) {
                formOf.add(FormOf(alternative, "alternative form", emptyList()))
                continue
            }
            val example = objects(s["examples"]).firstOrNull()?.let { ex ->
                str(ex["text"])?.let { Sense.Example(it, str(ex["translation"]) ?: str(ex["english"])) }
            }
            senses.add(Sense(gloss, tags, example))
        }
        return senses to formOf
    }

    private fun str(v: JsonElement?): String? {
        val p = v as? JsonPrimitive ?: return null
        if (!p.isString) return null
        return p.content.takeIf { it.isNotBlank() }
    }

    private fun strings(v: JsonElement?): List<String> =
        (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content } ?: emptyList()

    private fun objects(v: JsonElement?): List<JsonObject> = (v as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()
}
