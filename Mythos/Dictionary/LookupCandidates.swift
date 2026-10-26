import Foundation

nonisolated enum LookupCandidates {
    // Elided articles and pronouns: French l', d', qu', j'…; Italian dell', un'….
    private static let elision = try! NSRegularExpression(pattern: "^(\\p{L}{1,4})'(\\p{L}.*)$")

    /// Spellings to try, most likely first. Lookups are case- and
    /// apostrophe-sensitive: "Mange" at the start of a sentence is "mange",
    /// German nouns are capitalised, and Wiktionary titles use a straight apostrophe.
    static func candidates(for text: String) -> [String] {
        let word = text.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "’", with: "'")
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        guard !word.isEmpty else { return [] }
        var out = [word]
        let ns = word as NSString
        if let m = elision.firstMatch(in: word, range: NSRange(location: 0, length: ns.length)) {
            out.append(ns.substring(with: m.range(at: 2)))
        }
        for w in out {
            out.append(w.lowercased())
            out.append(w.prefix(1).uppercased() + w.dropFirst())
        }
        var seen = Set<String>()
        return out.filter { seen.insert($0).inserted }
    }

    /// A phrase's words, longest first, for looking it up by its main word:
    /// "se despierta" → "despierta", "se". Single words have none.
    static func mainWords(of text: String) -> [String] {
        let all = text.split(whereSeparator: { $0.isWhitespace }).map { $0.trimmingCharacters(in: .punctuationCharacters) }
        guard all.count > 1 else { return [] }
        // Clitics and articles ("se", "le", "a") are never the word to look up.
        let words = all.filter { $0.count > 2 }
        return words.enumerated().sorted { ($1.element.count, $0.offset) < ($0.element.count, $1.offset) }.map(\.element)
    }
}
