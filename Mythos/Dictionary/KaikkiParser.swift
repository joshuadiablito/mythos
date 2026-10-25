import Foundation

/// Parses kaikki.org's per-word JSONL files: one line per part of speech.
/// https://kaikki.org/dictionary/rawdata.html — ported from Paradigma's src/shared/kaikki.ts.
nonisolated enum KaikkiParser {
    static func url(language: String, word: String) -> URL {
        let scalars = Array(word.precomposedStringWithCanonicalMapping.unicodeScalars)
        let first = String(String.UnicodeScalarView(scalars.prefix(1)))
        let firstTwo = String(String.UnicodeScalarView(scalars.prefix(2)))
        let path = [language, "meaning", first, firstTwo, "\(word.precomposedStringWithCanonicalMapping).jsonl"]
            .map(encodeURIComponent)
            .joined(separator: "/")
        return URL(string: "https://kaikki.org/dictionary/\(path)")!
    }

    /// JavaScript's encodeURIComponent, which kaikki.org's paths are built with.
    private static func encodeURIComponent(_ s: String) -> String {
        var allowed = CharacterSet.alphanumerics.intersection(CharacterSet(charactersIn: Unicode.Scalar(0)...Unicode.Scalar(127)))
        allowed.insert(charactersIn: "-_.!~*'()")
        return s.addingPercentEncoding(withAllowedCharacters: allowed) ?? s
    }

    /// Tags marking bookkeeping rows in wiktextract's `forms`, not real word forms.
    private static let metaFormTags: Set<String> = [
        "table-tags", "inflection-template", "class", "romanization",
        "multiword-construction", // "avoir + past participle"
        "combined-form", // Spanish verb + clitic pronoun, e.g. "cómelo"
    ]

    // Conjugation tables sometimes carry pronunciation rows that wiktextract
    // mistakes for forms (French "mɑ̃ʒ" beside "mange"). These characters occur
    // in IPA but in no orthography we support. θ, β and χ are Greek letters, so aren't listed.
    private static let ipaOnly = CharacterSet(charactersIn: "ɑɐɒɔəɘɛɜɪʊʌʒʃʁʀŋɲɥɾʎðɣʔˈˌːɨʉɯɤɵɞɶʏʝʋɫ\u{303}")

    private static func hasIPA(_ s: String) -> Bool { s.unicodeScalars.contains { ipaOnly.contains($0) } }

    static func parse(_ jsonl: String) -> [Entry] {
        jsonl.split(separator: "\n").compactMap { line in
            // A truncated or malformed line loses that part of speech, not the lookup.
            guard let data = line.data(using: .utf8),
                  let raw = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }
            return parseEntry(raw)
        }
    }

    static func parseEntry(_ raw: [String: Any]) -> Entry? {
        guard let word = str(raw["word"]), let pos = str(raw["pos"]) else { return nil }
        let (senses, formOf) = parseSenses(raw["senses"])
        if senses.isEmpty && formOf.isEmpty { return nil }

        let sounds = objects(raw["sounds"])
        let ipa = sounds.lazy.compactMap { str($0["ipa"]) }.first
        let audio = sounds.lazy.compactMap { str($0["mp3_url"]) ?? str($0["ogg_url"]) }.first.flatMap(URL.init(string:))
        let head = objects(raw["head_templates"]).lazy.compactMap { str($0["expansion"]) }.first

        return Entry(word: word, pos: pos, head: head == word ? nil : head, ipa: ipa, audioURL: audio,
                     senses: senses, forms: parseForms(raw["forms"], word: word), formOf: formOf)
    }

    private static func parseForms(_ raw: Any?, word: String) -> [WordForm] {
        var seen = Set<String>()
        var forms: [WordForm] = []
        for f in objects(raw) {
            guard let form = str(f["form"])?.trimmingCharacters(in: .whitespaces), form != "-", form != "—" else { continue }
            let tags = strings(f["tags"])
            if tags.contains(where: metaFormTags.contains) { continue }
            if hasIPA(form) && !hasIPA(word) { continue }
            let key = "\(form)|\(tags.sorted().joined(separator: ","))"
            guard seen.insert(key).inserted else { continue }
            forms.append(WordForm(form: form, tags: tags, fromTable: f["source"] != nil && !(f["source"] is NSNull)))
        }
        return forms
    }

    private static func parseSenses(_ raw: Any?) -> ([Sense], [FormOf]) {
        var senses: [Sense] = []
        var formOf: [FormOf] = []
        for s in objects(raw) {
            // Nested senses list the parent gloss first; the last one is the specific meaning.
            guard let gloss = strings(s["glosses"]).last else { continue }
            let tags = strings(s["tags"])
            if let lemma = objects(s["form_of"]).lazy.compactMap({ str($0["word"]) }).first {
                // "…simple past of γράφω (gráfo)": drop the lemma and any romanisation after it.
                let pattern = "\\s+of\\s+\(NSRegularExpression.escapedPattern(for: lemma))(\\s*\\([^)]*\\))?$"
                let description = gloss.replacingOccurrences(of: pattern, with: "", options: .regularExpression)
                formOf.append(FormOf(lemma: lemma, description: description, tags: tags))
                continue
            }
            // "alternative form of ξεχνάω": the other spelling has the full entry and table.
            if let alternative = objects(s["alt_of"]).lazy.compactMap({ str($0["word"]) }).first {
                formOf.append(FormOf(lemma: alternative, description: "alternative form", tags: []))
                continue
            }
            let example = objects(s["examples"]).first.flatMap { ex -> Sense.Example? in
                guard let text = str(ex["text"]) else { return nil }
                return Sense.Example(text: text, translation: str(ex["translation"]) ?? str(ex["english"]))
            }
            senses.append(Sense(gloss: gloss, tags: tags, example: example))
        }
        return (senses, formOf)
    }

    private static func str(_ v: Any?) -> String? {
        guard let s = v as? String, !s.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
        return s
    }

    private static func strings(_ v: Any?) -> [String] { (v as? [Any])?.compactMap { $0 as? String } ?? [] }

    private static func objects(_ v: Any?) -> [[String: Any]] { (v as? [Any])?.compactMap { $0 as? [String: Any] } ?? [] }
}
