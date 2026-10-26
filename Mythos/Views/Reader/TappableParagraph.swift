import NaturalLanguage
import SwiftUI

/// A word tapped in a story.
struct WordTap: Equatable {
    let word: String
    let paragraph: Int
    let token: Int
}

/// A paragraph whose every word is a link. It stays a single `Text`, so it
/// wraps, scales with Dynamic Type and reads with VoiceOver like any text.
/// Glossary words get a dotted apricot underline; the tapped word is highlighted,
/// and so is the word being read aloud.
struct TappableParagraph: View {
    let text: String
    let index: Int
    let language: Language
    /// The story's glossary words and phrases.
    let vocab: [String]
    let selected: WordTap?
    /// The word being read aloud, as a UTF-16 range of `text`.
    var spoken: NSRange?
    var fontSize: CGFloat = 21
    let onTap: (WordTap) -> Void

    var body: some View {
        Text(attributed)
            .font(Theme.storyFont(size: fontSize))
            .lineSpacing(fontSize * 0.45)
            .foregroundStyle(Theme.ink)
            .tint(Theme.ink)
            .environment(\.openURL, OpenURLAction { url in
                guard let tap = Self.decode(url) else { return .discarded }
                onTap(tap)
                return .handled
            })
    }

    /// Where glossary words and phrases ("faire les courses") occur in the text.
    private var vocabRanges: [NSRange] {
        let ns = text as NSString
        return vocab.flatMap { phrase -> [NSRange] in
            var found: [NSRange] = []
            var search = NSRange(location: 0, length: ns.length)
            while true {
                let r = ns.range(of: phrase, options: [.caseInsensitive], range: search)
                guard r.location != NSNotFound else { break }
                // Whole words only: "lit" (bed) isn't in "lisait".
                let before = r.location == 0 ? " " : ns.substring(with: NSRange(location: r.location - 1, length: 1))
                let afterIndex = r.location + r.length
                let after = afterIndex >= ns.length ? " " : ns.substring(with: NSRange(location: afterIndex, length: 1))
                if !(before.first?.isLetter ?? false) && !(after.first?.isLetter ?? false) { found.append(r) }
                search = NSRange(location: afterIndex, length: ns.length - afterIndex)
            }
            return found
        }
    }

    private var attributed: AttributedString {
        let vocabRanges = vocabRanges
        var result = AttributedString()
        let tokenizer = NLTokenizer(unit: .word)
        tokenizer.string = text
        tokenizer.setLanguage(NLLanguage(language.baseCode))
        var cursor = text.startIndex
        var tokenIndex = 0
        tokenizer.enumerateTokens(in: text.startIndex..<text.endIndex) { range, _ in
            if cursor < range.lowerBound { result += AttributedString(String(text[cursor..<range.lowerBound])) }
            let word = String(text[range])
            var run = AttributedString(word)
            if word.contains(where: \.isLetter) {
                run.link = Self.encode(WordTap(word: word, paragraph: index, token: tokenIndex))
                let wordRange = NSRange(range, in: text)
                if vocabRanges.contains(where: { NSIntersectionRange($0, wordRange).length > 0 }) {
                    run.underlineStyle = Text.LineStyle(pattern: .dot, color: Theme.warm)
                }
                if selected?.paragraph == index && selected?.token == tokenIndex {
                    run.backgroundColor = Theme.accent.opacity(0.22)
                } else if let spoken, NSIntersectionRange(spoken, wordRange).length > 0 {
                    run.backgroundColor = Theme.warm.opacity(0.3)
                }
            }
            result += run
            tokenIndex += 1
            cursor = range.upperBound
            return true
        }
        if cursor < text.endIndex { result += AttributedString(String(text[cursor...])) }
        return result
    }

    private static func encode(_ tap: WordTap) -> URL {
        var c = URLComponents()
        c.scheme = "mythos-word"
        c.host = "lookup"
        c.queryItems = [.init(name: "w", value: tap.word), .init(name: "p", value: "\(tap.paragraph)"), .init(name: "t", value: "\(tap.token)")]
        return c.url!
    }

    private static func decode(_ url: URL) -> WordTap? {
        guard url.scheme == "mythos-word", let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems,
              let word = items.first(where: { $0.name == "w" })?.value,
              let p = items.first(where: { $0.name == "p" })?.value.flatMap(Int.init),
              let t = items.first(where: { $0.name == "t" })?.value.flatMap(Int.init) else { return nil }
        return WordTap(word: word, paragraph: p, token: t)
    }
}
