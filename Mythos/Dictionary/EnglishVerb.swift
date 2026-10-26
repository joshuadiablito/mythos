import Foundation

/// A verb's English meaning, conjugated to match a form: Wiktionary says
/// "mange" is the "third-person singular present indicative of manger", and
/// "manger" means "to eat", so "mange" is "he/she eats". This is what tells a
/// learner who is doing what.
nonisolated struct EnglishVerb: Hashable, Sendable {
    /// The verb itself: "eat", "wake", "be".
    let base: String
    /// Anything after it: "up" in "to wake up", "called" in "to be called".
    let rest: String

    /// From a gloss like "to eat", "(transitive) to look for, search" or
    /// "to wake (someone) up". Nil unless it reads as an English infinitive.
    init?(gloss: String) {
        var g = gloss.replacingOccurrences(of: "\\([^)]*\\)", with: "", options: .regularExpression)
        g = g.components(separatedBy: CharacterSet(charactersIn: ",;:")).first ?? g
        let words = g.split(whereSeparator: \.isWhitespace).map(String.init)
        guard words.count >= 2, words[0].lowercased() == "to", words[1].allSatisfy(\.isLetter) else { return nil }
        base = words[1].lowercased()
        rest = words.dropFirst(2).joined(separator: " ")
    }

    private init(base: String, rest: String) {
        self.base = base
        self.rest = rest
    }

    /// Every verb a gloss offers: "to wake, wake up" → wake, wake up.
    static func alternatives(inGloss gloss: String) -> [EnglishVerb] {
        guard let first = EnglishVerb(gloss: gloss) else { return [] }
        let cleaned = gloss.replacingOccurrences(of: "\\([^)]*\\)", with: "", options: .regularExpression)
        var verbs = [first]
        for part in cleaned.components(separatedBy: CharacterSet(charactersIn: ",;")).dropFirst() {
            var words = part.split(whereSeparator: \.isWhitespace).map(String.init)
            if words.first?.lowercased() == "to" { words.removeFirst() }
            guard let head = words.first, head.allSatisfy(\.isLetter) else { continue }
            let verb = EnglishVerb(base: head.lowercased(), rest: words.dropFirst().joined(separator: " "))
            if !verbs.contains(verb) { verbs.append(verb) }
        }
        return verbs
    }

    /// The English for a verb entry: its first meaning that is an infinitive.
    /// With a `hint` (the story's gloss, "wakes up"), the meaning that
    /// matches it, from any sense: "wake up" rather than "wake".
    init?(entry: Entry, hint: String? = nil) {
        var verbs: [EnglishVerb] = []
        for verb in entry.senses.flatMap({ EnglishVerb.alternatives(inGloss: $0.gloss) }) where !verbs.contains(verb) {
            verbs.append(verb)
        }
        guard entry.pos == "verb", !verbs.isEmpty else { return nil }
        let hintWords = Set((hint ?? "").lowercased().split(whereSeparator: { !$0.isLetter }).map(String.init))
        let fits = verbs.filter { verb in
            !verb.rest.isEmpty && verb.rest.lowercased().split(separator: " ").allSatisfy { hintWords.contains(String($0)) }
                && hintWords.contains { $0.hasPrefix(String(verb.base.prefix(3))) }
        }
        self = fits.max { $0.rest.count < $1.rest.count } ?? verbs[0]
    }

    // MARK: Who and when

    enum Tense: Hashable, Sendable {
        case present, past, imperfect, future, futureContinuous, conditional
        case presentSubjunctive, pastSubjunctive, perfect, pluperfect, imperative
    }

    /// The tense a form's (or table's) Wiktionary tags describe, if English has one like it.
    static func tense(for tags: some Sequence<String>) -> Tense? {
        let t = Set(tags)
        if t.contains("negative") || t.contains("dependent") || t.contains("infinitive") || t.contains("participle") { return nil }
        if t.contains("imperative") { return .imperative }
        if t.contains("conditional") { return .conditional }
        if t.contains("pluperfect") { return .pluperfect }
        if t.contains("perfect") && !t.contains("future") { return .perfect }
        // A form that is both indicative and subjunctive ("mange") reads best as indicative.
        if !t.contains("indicative") && (t.contains("subjunctive") || t.contains("subjunctive-i") || t.contains("subjunctive-ii")) {
            let past = t.contains("imperfect") || t.contains("past") || t.contains("preterite")
                || t.contains("imperfect-se") || t.contains("imperfect-ra") || t.contains("subjunctive-ii")
            return past ? .pastSubjunctive : .presentSubjunctive
        }
        if t.contains("future") { return t.contains("imperfective") ? .futureContinuous : .future }
        if t.contains("imperfect") || (t.contains("past") && t.contains("imperfective")) { return .imperfect }
        if t.contains("preterite") || t.contains("past") || t.contains("historic") { return .past }
        if t.contains("present") { return .present }
        return nil
    }

    /// The subjects a form's tags name: "first/third-person singular" → I, he/she.
    static func subjects(for tags: some Sequence<String>) -> [(person: String, number: String)] {
        let t = Set(tags)
        let persons = ["first-person", "second-person", "third-person"].filter(t.contains)
        let numbers = ["singular", "plural"].filter(t.contains)
        return numbers.flatMap { n in persons.map { ($0, n) } }
    }

    static func pronoun(person: String, number: String) -> String {
        switch (person, number) {
        case ("first-person", "singular"): "I"
        case ("second-person", "singular"): "you"
        case ("third-person", "singular"): "he/she"
        case ("first-person", "plural"): "we"
        case ("second-person", "plural"): "you all"
        default: "they"
        }
    }

    // MARK: Phrases

    /// "he/she eats", "we were eating", "they will eat", "eat!". Nil if the
    /// combination makes no sense in English.
    func phrase(person: String, number: String, tense: Tense) -> String? {
        let subject = Self.pronoun(person: person, number: number)
        let thirdSingular = person == "third-person" && number == "singular"
        let firstSingular = person == "first-person" && number == "singular"
        let plural = number == "plural" || person == "second-person"
        let verb: String
        switch tense {
        case .present:
            verb = base == "be" ? (firstSingular ? "am" : thirdSingular ? "is" : "are")
                : thirdSingular ? Self.thirdPerson(base) : base
        case .past:
            verb = base == "be" ? (plural ? "were" : "was") : Self.forms(base).past
        case .imperfect:
            // "I was knowing" isn't English: stative verbs keep the simple past.
            if base == "be" { verb = plural ? "were" : "was" }
            else if Self.stative.contains(base) { verb = Self.forms(base).past }
            else { verb = "\(plural ? "were" : "was") \(Self.ing(base))" }
        case .future:
            verb = "will \(base)"
        case .futureContinuous:
            verb = Self.stative.contains(base) || base == "be" ? "will \(base)" : "will be \(Self.ing(base))"
        case .conditional:
            verb = "would \(base)"
        case .presentSubjunctive:
            return "(that) \(subject) \(join(base))"
        case .pastSubjunctive:
            return "(if) \(subject) \(join(base == "be" ? "were" : Self.forms(base).past))"
        case .perfect:
            verb = "\(thirdSingular ? "has" : "have") \(Self.forms(base).participle)"
        case .pluperfect:
            verb = "had \(Self.forms(base).participle)"
        case .imperative:
            switch (person, number) {
            case ("second-person", _): return "\(join(base))!"
            case ("first-person", "plural"): return "let's \(join(base))!"
            case ("third-person", _): return "\(join(base))! (polite)"
            default: return nil
            }
        }
        return "\(subject) \(join(verb))"
    }

    /// The English for an untabled form: "to eat", "eating", "eaten".
    func phrase(forFormLabel label: String) -> String? {
        let l = label.lowercased()
        if l.contains("infinitive") { return "to \(join(base))" }
        if l.contains("gerund") || (l.contains("participle") && l.contains("present")) { return join(Self.ing(base)) }
        if l.contains("participle") && (l.contains("past") || l.contains("perfect")) { return join(Self.forms(base).participle) }
        return nil
    }

    private func join(_ verb: String) -> String { rest.isEmpty ? verb : "\(verb) \(rest)" }

    // MARK: English morphology

    /// Verbs that describe a state, which English doesn't put in the continuous.
    static let stative: Set<String> = [
        "be", "have", "know", "want", "like", "love", "hate", "need", "believe", "seem", "own", "understand",
        "prefer", "remember", "mean", "belong", "contain", "cost", "deserve", "exist", "matter", "owe",
        "possess", "recognize", "recognise", "suppose", "doubt", "wish", "realize", "realise", "consist",
    ]

    static func thirdPerson(_ v: String) -> String {
        switch v {
        case "have": return "has"
        case "do": return "does"
        case "go": return "goes"
        default: break
        }
        if ["s", "sh", "ch", "x", "z", "o"].contains(where: v.hasSuffix) { return v + "es" }
        if v.hasSuffix("y"), let before = v.dropLast().last, !"aeiou".contains(before) { return String(v.dropLast()) + "ies" }
        return v + "s"
    }

    static func ing(_ v: String) -> String {
        if v == "be" || v == "see" || v == "flee" { return v + "ing" }
        if v.hasSuffix("ie") { return String(v.dropLast(2)) + "ying" }
        if v.hasSuffix("e"), !v.hasSuffix("ee"), !v.hasSuffix("ye"), !v.hasSuffix("oe"), v.count > 2 { return String(v.dropLast()) + "ing" }
        if doublesFinalConsonant(v) { return v + String(v.last!) + "ing" }
        return v + "ing"
    }

    /// Simple past and past participle: irregular verbs from a table (with
    /// prefixes: "understand", "overcome"), the rest by rule.
    static func forms(_ v: String) -> (past: String, participle: String) {
        if let irregular = irregulars[v] { return irregular }
        for prefix in ["under", "over", "with", "out", "mis", "re", "un", "up", "fore", "be"] where v.hasPrefix(prefix) {
            if let irregular = irregulars[String(v.dropFirst(prefix.count))] {
                return (prefix + irregular.past, prefix + irregular.participle)
            }
        }
        let regular: String
        if v.hasSuffix("e") { regular = v + "d" }
        else if v.hasSuffix("y"), let before = v.dropLast().last, !"aeiou".contains(before) { regular = String(v.dropLast()) + "ied" }
        else if doublesFinalConsonant(v) { regular = v + String(v.last!) + "ed" }
        else { regular = v + "ed" }
        return (regular, regular)
    }

    /// stop → stopping, plan → planned: one-syllable words ending consonant–vowel–consonant.
    private static func doublesFinalConsonant(_ v: String) -> Bool {
        let chars = Array(v)
        guard chars.count >= 3 else { return false }
        let vowels = Set("aeiou")
        let last = chars[chars.count - 1], mid = chars[chars.count - 2], first = chars[chars.count - 3]
        let syllables = v.split(whereSeparator: { !vowels.contains($0) }).count
        return syllables == 1 && !vowels.contains(last) && !"wxy".contains(last) && vowels.contains(mid) && !vowels.contains(first)
    }

    private static let irregulars: [String: (past: String, participle: String)] = {
        let table = """
        arise arose arisen|awake awoke awoken|be was been|bear bore borne|beat beat beaten|become became become|\
        begin began begun|bend bent bent|bet bet bet|bind bound bound|bite bit bitten|bleed bled bled|blow blew blown|\
        break broke broken|breed bred bred|bring brought brought|build built built|burst burst burst|buy bought bought|\
        catch caught caught|choose chose chosen|cling clung clung|come came come|cost cost cost|creep crept crept|\
        cut cut cut|deal dealt dealt|dig dug dug|do did done|draw drew drawn|drink drank drunk|drive drove driven|\
        eat ate eaten|fall fell fallen|feed fed fed|feel felt felt|fight fought fought|find found found|flee fled fled|\
        fly flew flown|forbid forbade forbidden|forget forgot forgotten|forgive forgave forgiven|freeze froze frozen|\
        get got got|give gave given|go went gone|grind ground ground|grow grew grown|hang hung hung|have had had|\
        hear heard heard|hide hid hidden|hit hit hit|hold held held|hurt hurt hurt|keep kept kept|kneel knelt knelt|\
        know knew known|lay laid laid|lead led led|leap leapt leapt|leave left left|lend lent lent|let let let|\
        lie lay lain|light lit lit|lose lost lost|make made made|mean meant meant|meet met met|pay paid paid|\
        put put put|quit quit quit|read read read|ride rode ridden|ring rang rung|rise rose risen|run ran run|\
        say said said|see saw seen|seek sought sought|sell sold sold|send sent sent|set set set|sew sewed sewn|\
        shake shook shaken|shine shone shone|shoot shot shot|show showed shown|shrink shrank shrunk|shut shut shut|\
        sing sang sung|sink sank sunk|sit sat sat|sleep slept slept|slide slid slid|speak spoke spoken|speed sped sped|\
        spend spent spent|spin spun spun|spit spat spat|split split split|spread spread spread|spring sprang sprung|\
        stand stood stood|steal stole stolen|stick stuck stuck|sting stung stung|stink stank stunk|strike struck struck|\
        swear swore sworn|sweep swept swept|swim swam swum|swing swung swung|take took taken|teach taught taught|\
        tear tore torn|tell told told|think thought thought|throw threw thrown|wake woke woken|wear wore worn|\
        weep wept wept|win won won|wind wound wound|write wrote written|dream dreamt dreamt|smell smelt smelt|\
        spell spelt spelt|burn burnt burnt|learn learnt learnt|lean leant leant|spoil spoilt spoilt
        """
        var map: [String: (String, String)] = [:]
        for entry in table.split(separator: "|") {
            let w = entry.split(separator: " ").map(String.init)
            if w.count == 3 { map[w[0]] = (w[1], w[2]) }
        }
        return map
    }()
}
