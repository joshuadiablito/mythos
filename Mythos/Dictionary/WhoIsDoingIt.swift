import Foundation

/// One way to read a verb form: who it is, in the language, and what that
/// means in English. "il/elle achète" → "he/she buys".
nonisolated struct WhoLine: Hashable, Sendable {
    /// The form with its subject pronoun, or empty for a form without one (an infinitive).
    let subject: String
    let english: String
}

nonisolated extension EnglishVerb {
    /// The readings of one form-of sense: "je mange" and "il/elle mange" for
    /// "mange", each with its English. `form` is the word as it was looked up,
    /// clitics and all ("se despierta").
    static func whoLines(for formOf: FormOf, form: String, language: Language, verb: EnglishVerb?) -> [WhoLine] {
        guard let verb else { return [] }
        guard let tense = tense(for: formOf.tags) else {
            return verb.phrase(forFormLabel: formOf.description).map { [WhoLine(subject: "", english: $0)] } ?? []
        }
        let shown = form.lowercased()
        return subjects(for: formOf.tags).compactMap { person, number in
            guard let phrase = verb.phrase(person: person, number: number, tense: tense) else { return nil }
            let pronoun = tense == .imperative ? nil
                : InflectionBuilder.pronounFor(language, row: person, column: number, form: shown)
            let subject = pronoun.map { $0.hasSuffix("’") ? $0 + shown : "\($0) \(shown)" } ?? shown
            return WhoLine(subject: subject, english: phrase)
        }
    }
}

nonisolated extension LookupResult {
    /// Every reading of the looked-up word, for showing who does what without
    /// the full dictionary entry (on a flashcard). A verb's own infinitive reads
    /// "to wake up"; words that aren't verbs have none.
    /// `hint` is the word's gloss in its story ("wakes up"), to pick the English
    /// verb that matches it.
    func whoLines(language: Language, hint: String? = nil) -> [WhoLine] {
        var lines: [WhoLine] = []
        // A phrase with words before the verb ("se despierta") can't be a command:
        // those put their pronouns after it (¡despiértate!).
        let firstWord = query.split(whereSeparator: \.isWhitespace).first.map { $0.lowercased() }
        let preceded = entries.contains { $0.word.lowercased() != firstWord } && query.contains(" ")
        for formOf in entries.flatMap(\.formOf) where !(preceded && formOf.tags.contains("imperative")) {
            let verb = lemmas.first { $0.word == formOf.lemma }.flatMap { EnglishVerb(entry: $0, hint: hint) }
            for line in EnglishVerb.whoLines(for: formOf, form: query, language: language, verb: verb) where !lines.contains(line) {
                lines.append(line)
            }
        }
        if lines.isEmpty, let verb = entries.lazy.compactMap({ EnglishVerb(entry: $0, hint: hint) }).first,
           let infinitive = verb.phrase(forFormLabel: "infinitive") {
            lines.append(WhoLine(subject: "", english: infinitive))
        }
        return lines
    }
}
