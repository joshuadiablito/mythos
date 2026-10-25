import Foundation

// One part of speech of a word, from kaikki.org (English Wiktionary as JSON).
// Ported from Paradigma's src/shared/types.ts.

nonisolated struct WordForm: Hashable, Sendable {
    let form: String
    let tags: [String]
    /// From the inflection table, rather than the entry's head line.
    let fromTable: Bool
}

nonisolated struct Sense: Hashable, Sendable {
    struct Example: Hashable, Sendable {
        let text: String
        let translation: String?
    }

    let gloss: String
    let tags: [String]
    let example: Example?
}

/// "first/third-person singular present indicative" of a lemma.
nonisolated struct FormOf: Hashable, Sendable {
    let lemma: String
    let description: String
    let tags: [String]
}

nonisolated struct Entry: Hashable, Sendable, Identifiable {
    let word: String
    let pos: String
    /// The head line, e.g. "casa f (plural casas)".
    let head: String?
    let ipa: String?
    let audioURL: URL?
    let senses: [Sense]
    let forms: [WordForm]
    let formOf: [FormOf]

    var id: String { "\(word)|\(pos)" }

    /// "verb", "noun", "adj" → a readable label.
    var partOfSpeech: String {
        switch pos {
        case "adj": "adjective"
        case "adv": "adverb"
        case "prep": "preposition"
        case "conj": "conjunction"
        case "pron": "pronoun"
        case "det": "determiner"
        case "intj": "interjection"
        case "num": "numeral"
        case "name": "proper noun"
        default: pos
        }
    }
}

/// What the dictionary says about a looked-up word: its own entries (which may
/// just say it's a form of something), and the entries for those lemmas.
nonisolated struct LookupResult: Sendable {
    let query: String
    let entries: [Entry]
    let lemmas: [Entry]

    var all: [Entry] { entries + lemmas }

    var isEmpty: Bool { all.allSatisfy { $0.senses.isEmpty && $0.formOf.isEmpty } }

    var ipa: String? { entries.lazy.compactMap(\.ipa).first ?? lemmas.lazy.compactMap(\.ipa).first }

    /// A recording of the word itself; a lemma's recording is a different word.
    var audioURL: URL? { entries.lazy.compactMap(\.audioURL).first }

    /// A short meaning, to keep with a saved word.
    var shortMeaning: String? {
        all.lazy.flatMap(\.senses).first?.gloss
    }
}
