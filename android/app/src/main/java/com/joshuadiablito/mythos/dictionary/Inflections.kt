package com.joshuadiablito.mythos.dictionary

import com.joshuadiablito.mythos.model.Language

// Pivots wiktextract's flat, tagged form list into tables a learner can read:
// one table per tense/mood (verbs) or declension type (adjectives), with person
// or case down the side and gender/number across the top.
// Ported from the iOS app's Inflections.swift (Spanish shows Spain's tú/vosotros).

data class InflectionTable(
    /** Stable identifier: the table's grouping tags. */
    val id: String,
    val title: String,
    val rowHeading: String,
    val rows: List<Axis>,
    val columns: List<Axis>,
    /** cells[row][column]; a missing cell has no form. */
    val cells: Map<String, Map<String, Cell>>,
) {
    data class Axis(val key: String, val label: String)

    data class Cell(
        val forms: List<String>,
        /** Subject pronoun shown before the forms: "nous", or "j’" (no space). */
        val pronoun: String? = null,
    )

    val isVerbTable: Boolean get() = rowHeading == "Person"

    fun contains(word: String): Boolean {
        val w = word.lowercase()
        return cells.values.any { row -> row.values.any { cell -> cell.forms.any { it.lowercase() == w } } }
    }
}

data class Inflections(
    val tables: List<InflectionTable>,
    /** Forms that don't fit a grid: infinitive, participles, comparative, diminutives… */
    val other: List<Other>,
) {
    data class Other(val label: String, val forms: List<String>)

    val isEmpty: Boolean get() = tables.isEmpty() && other.isEmpty()
}

object InflectionBuilder {
    private val person = listOf("first-person", "second-person", "third-person")
    private val grammaticalCase = listOf(
        "nominative", "accusative", "genitive", "dative", "instrumental",
        "locative", "prepositional", "ablative", "vocative", "partitive",
    )
    private val gender = listOf("masculine", "feminine", "neuter", "common")
    private val number = listOf("singular", "dual", "plural")

    // Tags that distinguish variants of the same cell rather than a different table.
    private val cellVariant = setOf(
        "informal", "formal", "vos-form", "with-tú", "with-vos", "second-person-semantically",
        "definite", "indefinite", "includes-article", "without-article",
        "form-of", "rare", "archaic", "dated", "obsolete", "colloquial", "dialectal",
        "uncommon", "alternative", "also", "especially", "literary", "nonstandard",
        "before-vowel", "irregular", "progressive", "continuative",
    )

    // Order tags appear in a table title; anything unlisted follows in source order.
    private val titleOrder = listOf(
        "active", "passive",
        "comparative", "superlative",
        "strong", "weak", "mixed", "predicative", "attributive",
        "indicative", "subjunctive", "conditional", "imperative", "optative",
        "present", "imperfect", "imperfect-se", "imperfect-ra", "preterite", "historic", "past",
        "future", "perfect", "pluperfect", "anterior",
        "infinitive", "participle", "gerund", "negative",
    )

    private val labels = mapOf(
        "first-person" to "1st person",
        "second-person" to "2nd person",
        "third-person" to "3rd person",
        "strong" to "strong (no article)",
        "weak" to "weak (after a definite article)",
        "mixed" to "mixed (after an indefinite article)",
        "historic" to "simple",
        "imperfect-se" to "imperfect (-se)",
        "imperfect-ra" to "imperfect (-ra)",
    )

    // Tense + aspect pairs with a name of their own (Greek).
    private val combinedLabels = listOf(
        listOf("present", "imperfective") to "present",
        listOf("imperfect", "imperfective") to "imperfect (continuous past)",
        listOf("past", "perfective") to "simple past (aorist)",
        listOf("future", "imperfective") to "future continuous",
        listOf("future", "perfective") to "simple future",
        listOf("dependent", "perfective") to "dependent (after να, θα)",
        listOf("imperative", "imperfective") to "imperative, continuous",
        listOf("imperative", "perfective") to "imperative, simple",
    )

    private val romancePortuguese = mapOf(
        "first-person|singular" to "eu", "second-person|singular" to "tu", "third-person|singular" to "ele/ela/você",
        "first-person|plural" to "nós", "second-person|plural" to "vós", "third-person|plural" to "eles/elas/vocês",
    )

    // Subject pronouns shown before verb forms, keyed "person|number".
    private val pronouns: Map<Language, Map<String, String>> = mapOf(
        Language.FRENCH to mapOf("first-person|singular" to "je", "second-person|singular" to "tu", "third-person|singular" to "il/elle", "first-person|plural" to "nous", "second-person|plural" to "vous", "third-person|plural" to "ils/elles"),
        Language.SPANISH to mapOf("first-person|singular" to "yo", "second-person|singular" to "tú", "third-person|singular" to "él/ella/usted", "first-person|plural" to "nosotros", "second-person|plural" to "vosotros", "third-person|plural" to "ellos/ellas/ustedes"),
        Language.ITALIAN to mapOf("first-person|singular" to "io", "second-person|singular" to "tu", "third-person|singular" to "lui/lei", "first-person|plural" to "noi", "second-person|plural" to "voi", "third-person|plural" to "loro"),
        Language.PORTUGUESE to romancePortuguese,
        Language.PORTUGAL to romancePortuguese,
        Language.GERMAN to mapOf("first-person|singular" to "ich", "second-person|singular" to "du", "third-person|singular" to "er/sie/es", "first-person|plural" to "wir", "second-person|plural" to "ihr", "third-person|plural" to "sie"),
        Language.GREEK to mapOf("first-person|singular" to "εγώ", "second-person|singular" to "εσύ", "third-person|singular" to "αυτός/αυτή/αυτό", "first-person|plural" to "εμείς", "second-person|plural" to "εσείς", "third-person|plural" to "αυτοί/αυτές/αυτά"),
    )

    private fun label(tag: String) = labels[tag] ?: tag.replace("-", " ")

    private fun capitalise(s: String) = s.take(1).uppercase() + s.drop(1)

    private fun titleFor(group: List<String>): String {
        // Greek tags nearly every finite form "indicative"; the tense names say enough.
        val tags = if (group.any { it == "perfective" || it == "imperfective" }) group.filter { it != "indicative" } else group
        val used = mutableSetOf<String>()
        val parts = mutableListOf<String>()
        for (t in tags) {
            if (t in used) continue
            val rule = combinedLabels.firstOrNull { (need, _) -> t in need && need.all { it in tags } }
            if (rule != null) {
                used.addAll(rule.first)
                parts.add(rule.second)
            } else {
                parts.add(label(t))
            }
        }
        return capitalise(if (parts.isEmpty()) "Forms" else parts.joinToString(" "))
    }

    private class Classified(
        val form: String,
        val row: List<String>,
        val gender: List<String>,
        val number: List<String>,
        val group: List<String>,
        val vos: Boolean,
    )

    private fun classify(f: WordForm): Classified {
        val row = mutableListOf<String>()
        val g = mutableListOf<String>()
        val n = mutableListOf<String>()
        var group = mutableListOf<String>()
        for (t in f.tags) {
            when {
                t in person || t in grammaticalCase -> row.add(t)
                t in gender -> g.add(t)
                t in number -> n.add(t)
                t !in cellVariant -> group.add(t)
            }
        }
        // Wiktionary tags "imperfect-se" forms "imperfect" too, and French present participles "gerund".
        val redundant = mutableSetOf<String>()
        if (group.any { it.startsWith("imperfect-") }) redundant.add("imperfect")
        if ("participle" in group) redundant.add("gerund")
        group.removeAll { it in redundant }
        fun rank(t: String) = titleOrder.indexOf(t).let { if (it < 0) titleOrder.size else it }
        group = group.withIndex().sortedWith(compareBy({ rank(it.value) }, { it.index })).map { it.value }.toMutableList()
        return Classified(f.form, row, g, n, group, "vos-form" in f.tags)
    }

    private fun orderKey(s: String, order: List<String>): String =
        s.split(" ").filter { it.isNotEmpty() }.joinToString(".") { "%02d".format(order.indexOf(it)) }

    /** The subject pronoun for a person and number, before `form`: "nous", or "j’" before a vowel. */
    fun pronounFor(language: Language, row: String, column: String, form: String): String? {
        val p = pronouns[language]?.get("$row|$column")
        val first = form.lowercase().firstOrNull()
        if (p == "je" && first != null && first in "aeiouyhâàéèêëîïôûœ") return "j’"
        return p
    }

    /** Builds inflection tables for an entry. The lemma itself is added to
     *  head-line-only tables (Spanish adjectives list only the feminine and plural). */
    fun build(entry: Entry, language: Language): Inflections {
        val spanish = language == Language.SPANISH
        val hasTable = entry.forms.any { it.fromTable }
        val tableForms = if (hasTable) entry.forms.filter { it.fromTable } else entry.forms
        val tableTags = mutableMapOf<String, MutableList<Set<String>>>()
        for (f in tableForms) tableTags.getOrPut(f.form) { mutableListOf() }.add(f.tags.toSet())
        // A head-line form duplicates a table cell if the same spelling has (at least) its tags there.
        fun inTable(f: WordForm) = tableTags[f.form]?.any { tags -> f.tags.all { it in cellVariant || it in tags } } ?: false

        val groupOrder = mutableListOf<String>()
        val groups = mutableMapOf<String, MutableList<Classified>>()
        val otherOrder = mutableListOf<String>()
        val other = mutableMapOf<String, MutableList<String>>()
        fun addOther(key: String, form: String) {
            val list = other.getOrPut(key) { otherOrder.add(key); mutableListOf() }
            if (form !in list) list.add(form)
        }

        for (f in tableForms) {
            // German "keine schönen": the negative article adds a whole duplicate table.
            if ("negative" in f.tags && "includes-article" in f.tags) continue
            val c = classify(f)
            if (c.row.isEmpty() && c.gender.isEmpty() && c.number.isEmpty()) {
                addOther(capitalise(if (c.group.isEmpty()) "other" else c.group.joinToString(" ") { label(it) }), f.form)
                continue
            }
            val id = c.group.joinToString(" ")
            groups.getOrPut(id) { groupOrder.add(id); mutableListOf() }.add(c)
        }

        val base = groups[""]
        if (!hasTable && (entry.pos == "adj" || entry.pos == "noun") && base != null && base.none { it.form == entry.word }) {
            val gendered = base.any { it.gender.isNotEmpty() }
            base.add(0, Classified(entry.word, emptyList(), if (gendered) listOf("masculine") else emptyList(), listOf("singular"), emptyList(), false))
        }

        if (hasTable) {
            // Head-line forms not already in a table: comparatives, diminutives…
            for (f in entry.forms) {
                if (f.fromTable || inTable(f)) continue
                val tags = f.tags.filter { it !in cellVariant }
                addOther(capitalise(if (tags.isEmpty()) "other" else tags.joinToString(", ") { label(it) }), f.form)
            }
        }

        val tables = mutableListOf<InflectionTable>()
        for (id in groupOrder) {
            val forms = groups.getValue(id)
            // "bonita" is tagged only "feminine"; if the table has a number axis, it's singular.
            val hasNumber = forms.any { it.number.isNotEmpty() }
            val isVerbTable = forms.any { c -> c.row.any { it in person } }
            val cells = linkedMapOf<String, LinkedHashMap<String, InflectionTable.Cell>>()
            val rowKeys = mutableListOf<String>()
            val colKeys = mutableListOf<String>()

            for (c in forms) {
                val num = if (c.number.isEmpty() && hasNumber) listOf("singular") else c.number
                // Spain's forms: tú and vosotros, not vos.
                if (spanish && c.vos) continue
                val row = c.row.joinToString(" ")
                val col = (c.gender + num).joinToString(" ")
                if (row !in rowKeys) rowKeys.add(row)
                if (col !in colKeys) colKeys.add(col)
                val byCol = cells.getOrPut(row) { linkedMapOf() }
                val cell = byCol[col] ?: InflectionTable.Cell(emptyList())
                if (c.form !in cell.forms) byCol[col] = cell.copy(forms = cell.forms + c.form)
            }

            if (isVerbTable && "imperative" !in id.split(" ")) {
                for ((row, byCol) in cells) {
                    for ((col, cell) in byCol.toMap()) {
                        byCol[col] = cell.copy(pronoun = pronounFor(language, row, col, cell.forms.firstOrNull() ?: ""))
                    }
                }
            }

            // Rows keep Wiktionary's order, which follows each language's convention.
            val rows = rowKeys.map { InflectionTable.Axis(it, capitalise(label(it))) }
            val columnOrder = gender + number
            val columns = colKeys.sortedBy { orderKey(it, columnOrder) }.map { key ->
                InflectionTable.Axis(key, capitalise(key.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { label(it) }))
            }
            tables.add(
                InflectionTable(
                    id = id,
                    title = titleFor(id.split(" ").filter { it.isNotEmpty() }),
                    rowHeading = if (isVerbTable) "Person" else if (rows.any { it.key.isNotEmpty() }) "Case" else "",
                    rows = rows,
                    columns = columns,
                    cells = cells,
                )
            )
        }

        // Tables by person (the tenses) come first, keeping Wiktionary's order within each group.
        val ordered = tables.filter { it.isVerbTable } + tables.filter { !it.isVerbTable }
        return Inflections(ordered, otherOrder.map { Inflections.Other(it, other.getValue(it)) })
    }
}
