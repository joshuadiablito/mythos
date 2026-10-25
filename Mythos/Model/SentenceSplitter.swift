import Foundation

/// Splits a paragraph into sentences, for showing each beside its translation.
///
/// Apple's sentence tokenizer leaves closing quotation marks at the start of
/// the next sentence ("» Camille répond…"), so this keeps dialogue together:
/// it never splits inside quotation marks, and a sentence ends after . ! ? …
/// (and Greek's question mark, ;) plus any closing quotes, when what follows
/// starts a new sentence. `scripts/segment.swift` uses this file too, so the
/// translations in the catalog are written against exactly these sentences.
nonisolated enum SentenceSplitter {
    static func split(_ text: String, languageCode: String) -> [String] {
        let chars = Array(text)
        var terminators: Set<Character> = [".", "!", "?", "…"]
        if languageCode == "el" { terminators.insert(";") }
        // German quotes „like this“; the others «like this» or “like this”.
        let opening: Set<Character> = languageCode == "de" ? ["„", "«", "‹"] : ["«", "“", "„", "‹"]
        let closing: Set<Character> = languageCode == "de" ? ["“", "»", "›"] : ["»", "”", "›"]
        // A dash starts a new line of dialogue, unless it introduces a speech
        // tag: "— Quanto é? — pergunta ela." is one sentence.
        func startsSentence(at index: Int) -> Bool {
            let c = chars[index]
            if "—–".contains(c) {
                return chars[(index + 1)...].first(where: \.isLetter)?.isUppercase ?? false
            }
            return c.isUppercase || opening.contains(c) || "¿¡\"".contains(c)
        }

        var sentences: [String] = []
        var start = 0
        var depth = 0
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if opening.contains(c) { depth += 1 }
            else if closing.contains(c) { depth = max(0, depth - 1) }

            if terminators.contains(c) {
                // Take repeated punctuation ("?!", "...") and closing quotes, with any spaces between ("? »").
                var end = i + 1
                var closingDepth = depth
                while end < chars.count {
                    let n = chars[end]
                    if terminators.contains(n) { end += 1 }
                    else if closing.contains(n) { closingDepth = max(0, closingDepth - 1); end += 1 }
                    else if n == " " || n == "\u{00A0}", end + 1 < chars.count,
                            closing.contains(chars[end + 1]) || terminators.contains(chars[end + 1]) { end += 1 }
                    else { break }
                }
                var next = end
                while next < chars.count, chars[next].isWhitespace { next += 1 }
                if closingDepth == 0 && (next == chars.count || (next > end && startsSentence(at: next))) {
                    sentences.append(String(chars[start..<end]).trimmingCharacters(in: .whitespaces))
                    depth = 0
                    start = next
                    i = next
                    continue
                }
                depth = closingDepth
                i = end
                continue
            }
            i += 1
        }
        let rest = String(chars[min(start, chars.count)...]).trimmingCharacters(in: .whitespaces)
        if !rest.isEmpty { sentences.append(rest) }
        return sentences
    }
}
