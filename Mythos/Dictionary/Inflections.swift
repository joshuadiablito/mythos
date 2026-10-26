import Foundation

// Pivots wiktextract's flat, tagged form list into tables a learner can read:
// one table per tense/mood (verbs) or declension type (adjectives), with person
// or case down the side and gender/number across the top.
// Ported from Paradigma's src/shared/inflections.ts (Spanish shows Spain's tú/vosotros).

nonisolated struct InflectionTable: Identifiable, Hashable, Sendable {
    struct Axis: Hashable, Sendable {
        let key: String
        let label: String
    }

    struct Cell: Hashable, Sendable {
        var forms: [String]
        /// Subject pronoun shown before the forms: "nous", or "j’" (no space).
        var pronoun: String?
    }

    /// Stable identifier: the table's grouping tags.
    let id: String
    let title: String
    let rowHeading: String
    let rows: [Axis]
    let columns: [Axis]
    /// cells[row][column]; a missing cell has no form.
    let cells: [String: [String: Cell]]

    var isVerbTable: Bool { rowHeading == "Person" }

    func contains(_ word: String) -> Bool {
        let w = word.lowercased()
        return cells.values.contains { $0.values.contains { $0.forms.contains { $0.lowercased() == w } } }
    }
}

nonisolated struct Inflections: Hashable, Sendable {
    struct Other: Hashable, Sendable {
        let label: String
        let forms: [String]
    }

    let tables: [InflectionTable]
    /// Forms that don't fit a grid: infinitive, participles, comparative, diminutives…
    let other: [Other]

    var isEmpty: Bool { tables.isEmpty && other.isEmpty }
}

nonisolated enum InflectionBuilder {
    private static let person = ["first-person", "second-person", "third-person"]
    private static let grammaticalCase = [
        "nominative", "accusative", "genitive", "dative", "instrumental",
        "locative", "prepositional", "ablative", "vocative", "partitive",
    ]
    private static let gender = ["masculine", "feminine", "neuter", "common"]
    private static let number = ["singular", "dual", "plural"]

    // Tags that distinguish variants of the same cell rather than a different table.
    private static let cellVariant: Set<String> = [
        "informal", "formal", "vos-form", "with-tú", "with-vos", "second-person-semantically",
        "definite", "indefinite", "includes-article", "without-article",
        "form-of", "rare", "archaic", "dated", "obsolete", "colloquial", "dialectal",
        "uncommon", "alternative", "also", "especially", "literary", "nonstandard",
        "before-vowel", "irregular", "progressive", "continuative",
    ]

    // Order tags appear in a table title; anything unlisted follows in source order.
    private static let titleOrder = [
        "active", "passive",
        "comparative", "superlative",
        "strong", "weak", "mixed", "predicative", "attributive",
        "indicative", "subjunctive", "conditional", "imperative", "optative",
        "present", "imperfect", "imperfect-se", "imperfect-ra", "preterite", "historic", "past",
        "future", "perfect", "pluperfect", "anterior",
        "infinitive", "participle", "gerund", "negative",
    ]

    private static let labels: [String: String] = [
        "first-person": "1st person",
        "second-person": "2nd person",
        "third-person": "3rd person",
        "strong": "strong (no article)",
        "weak": "weak (after a definite article)",
        "mixed": "mixed (after an indefinite article)",
        "historic": "simple",
        "imperfect-se": "imperfect (-se)",
        "imperfect-ra": "imperfect (-ra)",
    ]

    // Tense + aspect pairs with a name of their own (Greek).
    private static let combinedLabels: [([String], String)] = [
        (["present", "imperfective"], "present"),
        (["imperfect", "imperfective"], "imperfect (continuous past)"),
        (["past", "perfective"], "simple past (aorist)"),
        (["future", "imperfective"], "future continuous"),
        (["future", "perfective"], "simple future"),
        (["dependent", "perfective"], "dependent (after να, θα)"),
        (["imperative", "imperfective"], "imperative, continuous"),
        (["imperative", "perfective"], "imperative, simple"),
    ]

    // Subject pronouns shown before verb forms, keyed "person|number".
    private static let pronouns: [Language: [String: String]] = [
        .french: ["first-person|singular": "je", "second-person|singular": "tu", "third-person|singular": "il/elle", "first-person|plural": "nous", "second-person|plural": "vous", "third-person|plural": "ils/elles"],
        .spanish: ["first-person|singular": "yo", "second-person|singular": "tú", "third-person|singular": "él/ella/usted", "first-person|plural": "nosotros", "second-person|plural": "vosotros", "third-person|plural": "ellos/ellas/ustedes"],
        .italian: ["first-person|singular": "io", "second-person|singular": "tu", "third-person|singular": "lui/lei", "first-person|plural": "noi", "second-person|plural": "voi", "third-person|plural": "loro"],
        .portuguese: ["first-person|singular": "eu", "second-person|singular": "tu", "third-person|singular": "ele/ela/você", "first-person|plural": "nós", "second-person|plural": "vós", "third-person|plural": "eles/elas/vocês"],
        .portugal: ["first-person|singular": "eu", "second-person|singular": "tu", "third-person|singular": "ele/ela/você", "first-person|plural": "nós", "second-person|plural": "vós", "third-person|plural": "eles/elas/vocês"],
        .german: ["first-person|singular": "ich", "second-person|singular": "du", "third-person|singular": "er/sie/es", "first-person|plural": "wir", "second-person|plural": "ihr", "third-person|plural": "sie"],
        .greek: ["first-person|singular": "εγώ", "second-person|singular": "εσύ", "third-person|singular": "αυτός/αυτή/αυτό", "first-person|plural": "εμείς", "second-person|plural": "εσείς", "third-person|plural": "αυτοί/αυτές/αυτά"],
    ]

    private static func label(_ tag: String) -> String { labels[tag] ?? tag.replacingOccurrences(of: "-", with: " ") }

    private static func capitalise(_ s: String) -> String { s.prefix(1).uppercased() + s.dropFirst() }

    private static func titleFor(_ group: [String]) -> String {
        // Greek tags nearly every finite form "indicative"; the tense names say enough.
        let tags = group.contains { $0 == "perfective" || $0 == "imperfective" } ? group.filter { $0 != "indicative" } : group
        var used = Set<String>()
        var parts: [String] = []
        for t in tags where !used.contains(t) {
            if let rule = combinedLabels.first(where: { need, _ in need.contains(t) && need.allSatisfy(tags.contains) }) {
                rule.0.forEach { used.insert($0) }
                parts.append(rule.1)
            } else {
                parts.append(label(t))
            }
        }
        return capitalise(parts.isEmpty ? "Forms" : parts.joined(separator: " "))
    }

    private struct Classified {
        let form: String
        var row: [String] = []
        var gender: [String] = []
        var number: [String] = []
        var group: [String] = []
        let vos: Bool
    }

    private static func classify(_ f: WordForm) -> Classified {
        var c = Classified(form: f.form, vos: f.tags.contains("vos-form"))
        for t in f.tags {
            if person.contains(t) || grammaticalCase.contains(t) { c.row.append(t) }
            else if gender.contains(t) { c.gender.append(t) }
            else if number.contains(t) { c.number.append(t) }
            else if !cellVariant.contains(t) { c.group.append(t) }
        }
        // Wiktionary tags "imperfect-se" forms "imperfect" too, and French present participles "gerund".
        var redundant = Set<String>()
        if c.group.contains(where: { $0.hasPrefix("imperfect-") }) { redundant.insert("imperfect") }
        if c.group.contains("participle") { redundant.insert("gerund") }
        c.group.removeAll { redundant.contains($0) }
        let rank = { (t: String) in titleOrder.firstIndex(of: t) ?? titleOrder.count }
        c.group = c.group.enumerated().sorted { (rank($0.element), $0.offset) < (rank($1.element), $1.offset) }.map(\.element)
        return c
    }

    private static func orderKey(_ s: String, _ order: [String]) -> String {
        s.split(separator: " ").map { t in
            let i = order.firstIndex(of: String(t)) ?? -1
            return String(format: "%02d", i)
        }.joined(separator: ".")
    }

    /// The subject pronoun for a person and number, before `form`: "nous", or "j’" before a vowel.
    static func pronounFor(_ language: Language, row: String, column: String, form: String) -> String? {
        let p = pronouns[language]?["\(row)|\(column)"]
        if p == "je", let first = form.lowercased().first, "aeiouyhâàéèêëîïôûœ".contains(first) { return "j’" }
        return p
    }

    /// Builds inflection tables for an entry. The lemma itself is added to
    /// head-line-only tables (Spanish adjectives list only the feminine and plural).
    static func build(_ entry: Entry, language: Language) -> Inflections {
        let spanish = language == .spanish
        let hasTable = entry.forms.contains { $0.fromTable }
        let tableForms = hasTable ? entry.forms.filter(\.fromTable) : entry.forms
        var tableTags: [String: [Set<String>]] = [:]
        for f in tableForms { tableTags[f.form, default: []].append(Set(f.tags)) }
        // A head-line form duplicates a table cell if the same spelling has (at least) its tags there.
        let inTable = { (f: WordForm) in
            tableTags[f.form]?.contains { tags in f.tags.allSatisfy { cellVariant.contains($0) || tags.contains($0) } } ?? false
        }

        var groupOrder: [String] = []
        var groups: [String: [Classified]] = [:]
        var otherOrder: [String] = []
        var other: [String: [String]] = [:]
        func addOther(_ key: String, _ form: String) {
            if other[key] == nil { otherOrder.append(key) }
            if !(other[key]?.contains(form) ?? false) { other[key, default: []].append(form) }
        }

        for f in tableForms {
            // German "keine schönen": the negative article adds a whole duplicate table.
            if f.tags.contains("negative") && f.tags.contains("includes-article") { continue }
            let c = classify(f)
            if c.row.isEmpty && c.gender.isEmpty && c.number.isEmpty {
                addOther(capitalise(c.group.isEmpty ? "other" : c.group.map(label).joined(separator: " ")), f.form)
                continue
            }
            let id = c.group.joined(separator: " ")
            if groups[id] == nil { groupOrder.append(id) }
            groups[id, default: []].append(c)
        }

        if !hasTable && (entry.pos == "adj" || entry.pos == "noun"), var base = groups[""], !base.contains(where: { $0.form == entry.word }) {
            let gendered = base.contains { !$0.gender.isEmpty }
            base.insert(Classified(form: entry.word, row: [], gender: gendered ? ["masculine"] : [], number: ["singular"], group: [], vos: false), at: 0)
            groups[""] = base
        }

        if hasTable {
            // Head-line forms not already in a table: comparatives, diminutives…
            for f in entry.forms where !f.fromTable && !inTable(f) {
                let tags = f.tags.filter { !cellVariant.contains($0) }
                addOther(capitalise(tags.isEmpty ? "other" : tags.map(label).joined(separator: ", ")), f.form)
            }
        }

        var tables: [InflectionTable] = []
        for id in groupOrder {
            let forms = groups[id]!
            // "bonita" is tagged only "feminine"; if the table has a number axis, it's singular.
            let hasNumber = forms.contains { !$0.number.isEmpty }
            let isVerbTable = forms.contains { $0.row.contains(where: person.contains) }
            var cells: [String: [String: InflectionTable.Cell]] = [:]
            var rowKeys: [String] = []
            var colKeys: [String] = []

            for c in forms {
                let num = c.number.isEmpty && hasNumber ? ["singular"] : c.number
                // Spain's forms: tú and vosotros, not vos.
                if spanish && c.vos { continue }
                let row = c.row.joined(separator: " ")
                let col = (c.gender + num).joined(separator: " ")
                if !rowKeys.contains(row) { rowKeys.append(row) }
                if !colKeys.contains(col) { colKeys.append(col) }
                var cell = cells[row]?[col] ?? InflectionTable.Cell(forms: [])
                if !cell.forms.contains(c.form) { cell.forms.append(c.form) }
                cells[row, default: [:]][col] = cell
            }

            if isVerbTable && !id.split(separator: " ").contains("imperative") {
                for (row, byCol) in cells {
                    for (col, cell) in byCol {
                        cells[row]![col]!.pronoun = pronounFor(language, row: row, column: col, form: cell.forms.first ?? "")
                    }
                }
            }

            // Rows keep Wiktionary's order, which follows each language's convention.
            let rows = rowKeys.map { InflectionTable.Axis(key: $0, label: capitalise(label($0))) }
            let columnOrder = gender + number
            let columns = colKeys
                .sorted { orderKey($0, columnOrder) < orderKey($1, columnOrder) }
                .map { key in InflectionTable.Axis(key: key, label: capitalise(key.split(separator: " ").map { label(String($0)) }.joined(separator: " "))) }
            tables.append(InflectionTable(
                id: id,
                title: titleFor(id.split(separator: " ").map(String.init)),
                rowHeading: isVerbTable ? "Person" : rows.contains { !$0.key.isEmpty } ? "Case" : "",
                rows: rows,
                columns: columns,
                cells: cells))
        }

        // Tables by person (the tenses) come first, keeping Wiktionary's order within each group.
        let ordered = tables.filter(\.isVerbTable) + tables.filter { !$0.isVerbTable }
        return Inflections(tables: ordered, other: otherOrder.map { Inflections.Other(label: $0, forms: other[$0]!) })
    }
}
