import Foundation

struct VocabItem: Codable, Hashable, Sendable {
    /// The word as it appears in the story.
    let word: String
    /// Its meaning in English, in this story.
    let gloss: String
}

/// One sentence of a story and its English translation.
struct SentencePair: Hashable, Sendable {
    let original: String
    let translation: String
}

struct Story: Identifiable, Hashable, Sendable {
    let id: String
    let language: Language
    let level: Level
    /// Position in its level's catalog, from 1.
    let number: Int
    let title: String
    /// The title in English.
    let subtitle: String
    let topic: String
    /// SF Symbol for the topic.
    let symbol: String
    let paragraphs: [String]
    /// Each paragraph's sentences beside their English translations. Empty
    /// if the story has no translation, or one that doesn't line up.
    let sentencePairs: [[SentencePair]]
    let vocab: [VocabItem]

    var text: String { paragraphs.joined(separator: "\n\n") }

    var wordCount: Int {
        paragraphs.reduce(0) { $0 + $1.split(whereSeparator: \.isWhitespace).count }
    }

    /// At a learner's pace of about 50 words a minute.
    var readingMinutes: Int { max(1, Int((Double(wordCount) / 50).rounded())) }

    /// The first sentence, for notification previews.
    var opening: String {
        let first = paragraphs.first ?? ""
        guard let end = first.firstIndex(where: { ".!?;".contains($0) }) else { return first }
        return String(first[...end])
    }

    /// Pairs each paragraph's sentences, as `SentenceSplitter` splits them,
    /// with `english`: per paragraph, one translation per sentence. Done once,
    /// when the catalog loads, rather than each time the reader is drawn.
    static func sentencePairs(paragraphs: [String], english: [[String]], language: Language) -> [[SentencePair]] {
        guard english.count == paragraphs.count else { return [] }
        var pairs: [[SentencePair]] = []
        for (paragraph, translations) in zip(paragraphs, english) {
            let sentences = SentenceSplitter.split(paragraph, languageCode: language.rawValue)
            guard sentences.count == translations.count else { return [] }
            pairs.append(zip(sentences, translations).map(SentencePair.init))
        }
        return pairs
    }

    func gloss(for word: String) -> String? {
        let w = word.lowercased()
        return vocab.first { $0.word.lowercased() == w }?.gloss
    }
}

/// The JSON shape of one bundled catalog file: one language at one level.
struct CatalogFile: Decodable {
    struct Record: Decodable {
        let id: String
        let title: String
        let subtitle: String
        let topic: String
        let symbol: String
        let paragraphs: [String]
        let english: [[String]]?
        let vocab: [VocabItem]
    }

    let language: Language
    let level: Level
    let stories: [Record]

    var resolved: [Story] {
        stories.enumerated().map { index, r in
            Story(id: r.id, language: language, level: level, number: index + 1, title: r.title, subtitle: r.subtitle,
                  topic: r.topic, symbol: r.symbol, paragraphs: r.paragraphs,
                  sentencePairs: Story.sentencePairs(paragraphs: r.paragraphs, english: r.english ?? [], language: language),
                  vocab: r.vocab)
        }
    }
}
