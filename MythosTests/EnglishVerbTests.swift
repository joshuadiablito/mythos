import Foundation
import Testing
@testable import Mythos

private final class FixtureBundle {}

@Suite("English for verb forms")
struct EnglishVerbTests {
    @Test func glossesBecomeVerbs() throws {
        let eat = try #require(EnglishVerb(gloss: "to eat"))
        #expect(eat.base == "eat" && eat.rest.isEmpty)
        let wake = try #require(EnglishVerb(gloss: "to wake (someone) up"))
        #expect(wake.base == "wake" && wake.rest == "up")
        #expect(EnglishVerb(gloss: "(transitive) to look for, search")?.rest == "for")
        #expect(EnglishVerb(gloss: "house") == nil)
        #expect(EnglishVerb(gloss: "inflection of manger:") == nil)
    }

    @Test func whoIsDoingIt() throws {
        let eat = try #require(EnglishVerb(gloss: "to eat"))
        #expect(eat.phrase(person: "third-person", number: "singular", tense: .present) == "he/she eats")
        #expect(eat.phrase(person: "first-person", number: "plural", tense: .present) == "we eat")
        #expect(eat.phrase(person: "third-person", number: "plural", tense: .past) == "they ate")
        #expect(eat.phrase(person: "first-person", number: "singular", tense: .imperfect) == "I was eating")
        #expect(eat.phrase(person: "second-person", number: "plural", tense: .future) == "you all will eat")
        #expect(eat.phrase(person: "third-person", number: "singular", tense: .conditional) == "he/she would eat")
        #expect(eat.phrase(person: "third-person", number: "singular", tense: .perfect) == "he/she has eaten")
        #expect(eat.phrase(person: "second-person", number: "singular", tense: .imperative) == "eat!")
        #expect(eat.phrase(person: "first-person", number: "plural", tense: .imperative) == "let's eat!")
        #expect(eat.phrase(person: "third-person", number: "singular", tense: .presentSubjunctive) == "(that) he/she eat")
    }

    @Test func englishMorphology() throws {
        let be = try #require(EnglishVerb(gloss: "to be called"))
        #expect(be.phrase(person: "first-person", number: "singular", tense: .present) == "I am called")
        #expect(be.phrase(person: "third-person", number: "plural", tense: .past) == "they were called")
        #expect(EnglishVerb(gloss: "to stop")?.phrase(person: "first-person", number: "singular", tense: .imperfect) == "I was stopping")
        #expect(EnglishVerb(gloss: "to try")?.phrase(person: "third-person", number: "singular", tense: .present) == "he/she tries")
        #expect(EnglishVerb(gloss: "to watch")?.phrase(person: "third-person", number: "singular", tense: .present) == "he/she watches")
        #expect(EnglishVerb(gloss: "to understand")?.phrase(person: "first-person", number: "singular", tense: .past) == "I understood")
        // Stative verbs don't go into the continuous.
        #expect(EnglishVerb(gloss: "to know")?.phrase(person: "first-person", number: "singular", tense: .imperfect) == "I knew")
        #expect(EnglishVerb(gloss: "to live")?.phrase(person: "first-person", number: "singular", tense: .imperfect) == "I was living")
    }

    @Test func tagsBecomeTenses() {
        // "mange": first/third-person singular present indicative/subjunctive.
        #expect(EnglishVerb.tense(for: ["first-person", "indicative", "present", "singular", "subjunctive", "third-person"]) == .present)
        #expect(EnglishVerb.tense(for: ["indicative", "past", "perfective"]) == .past) // Greek aorist
        #expect(EnglishVerb.tense(for: ["imperfect", "imperfective", "indicative"]) == .imperfect)
        #expect(EnglishVerb.tense(for: ["future", "imperfective", "indicative"]) == .futureContinuous)
        #expect(EnglishVerb.tense(for: ["indicative", "preterite"]) == .past)
        #expect(EnglishVerb.tense(for: ["subjunctive", "imperfect-ra"]) == .pastSubjunctive)
        #expect(EnglishVerb.tense(for: ["dependent", "perfective"]) == nil)
        let subjects = EnglishVerb.subjects(for: ["first-person", "third-person", "singular", "present"])
        #expect(subjects.map(\.person) == ["first-person", "third-person"])
    }

    @Test func spanishPastFromARealEntry() throws {
        let url = try #require(Bundle(for: FixtureBundle.self).url(forResource: "spanish-comia", withExtension: "jsonl"))
        let form = try #require(KaikkiParser.parse(try String(contentsOf: url, encoding: .utf8)).flatMap(\.formOf).first { $0.lemma == "comer" })
        let tense = try #require(EnglishVerb.tense(for: form.tags))
        let eat = try #require(EnglishVerb(gloss: "to eat"))
        let phrases = EnglishVerb.subjects(for: form.tags).compactMap { eat.phrase(person: $0.person, number: $0.number, tense: tense) }
        #expect(phrases.contains("he/she was eating"))
    }
}

@Suite("Who is doing it")
struct WhoIsDoingItTests {
    private func despierta() -> LookupResult {
        let form = Entry(word: "despierta", pos: "verb", head: nil, ipa: nil, audioURL: nil, senses: [], forms: [],
                         formOf: [FormOf(lemma: "despertar", description: "third-person singular present indicative",
                                         tags: ["third-person", "singular", "present", "indicative"])])
        let lemma = Entry(word: "despertar", pos: "verb", head: nil, ipa: nil, audioURL: nil,
                          senses: [Sense(gloss: "to wake up", tags: [], example: nil)], forms: [], formOf: [])
        return LookupResult(query: "se despierta", entries: [form], lemmas: [lemma])
    }

    @Test func aPhraseIsLookedUpByItsMainWord() {
        #expect(LookupCandidates.mainWords(of: "se despierta") == ["despierta"])
        #expect(LookupCandidates.mainWords(of: "a eu peur") == ["peur"])
        #expect(LookupCandidates.mainWords(of: "zieht sich an") == ["zieht", "sich"])
        #expect(LookupCandidates.mainWords(of: "despierta").isEmpty)
    }

    @Test func theReadingKeepsTheWholePhraseAndSaysWho() {
        let lines = despierta().whoLines(language: .spanish)
        #expect(lines == [WhoLine(subject: "él/ella/usted se despierta", english: "he/she wakes up")])
    }

    @Test func anInfinitiveReadsAsOne() {
        let lemma = Entry(word: "despertar", pos: "verb", head: nil, ipa: nil, audioURL: nil,
                          senses: [Sense(gloss: "to wake up", tags: [], example: nil)], forms: [], formOf: [])
        let lines = LookupResult(query: "despertar", entries: [lemma], lemmas: []).whoLines(language: .spanish)
        #expect(lines == [WhoLine(subject: "", english: "to wake up")])
    }
}

@Suite("Choosing the English verb")
struct EnglishVerbChoiceTests {
    private func despertar(_ gloss: String) -> Entry {
        Entry(word: "despertar", pos: "verb", head: nil, ipa: nil, audioURL: nil,
              senses: [Sense(gloss: gloss, tags: [], example: nil)], forms: [], formOf: [])
    }

    @Test func theStoryGlossPicksTheAlternative() throws {
        #expect(EnglishVerb.alternatives(inGloss: "to wake, wake up").map(\.rest) == ["", "up"])
        #expect(try #require(EnglishVerb(entry: despertar("to wake, wake up"), hint: "wakes up")).rest == "up")
        // Without a hint, or with one that doesn't fit, the first meaning stands.
        #expect(try #require(EnglishVerb(entry: despertar("to wake, wake up"))).rest == "")
        #expect(try #require(EnglishVerb(entry: despertar("to wake, wake up"), hint: "rouses")).rest == "")
        // Wiktionary often gives each meaning its own sense.
        let senses = Entry(word: "despertar", pos: "verb", head: nil, ipa: nil, audioURL: nil,
                           senses: ["to awaken", "to wake up", "to wake"].map { Sense(gloss: $0, tags: [], example: nil) }, forms: [], formOf: [])
        #expect(try #require(EnglishVerb(entry: senses, hint: "wakes up")).rest == "up")
        #expect(try #require(EnglishVerb(entry: senses)).base == "awaken")
    }

    @Test func aPronounBeforeTheVerbRulesOutACommand() {
        let form = Entry(word: "despierta", pos: "verb", head: nil, ipa: nil, audioURL: nil, senses: [], forms: [], formOf: [
            FormOf(lemma: "despertar", description: "third-person singular present indicative", tags: ["third-person", "singular", "present", "indicative"]),
            FormOf(lemma: "despertar", description: "second-person singular imperative", tags: ["second-person", "singular", "imperative"]),
        ])
        let phrase = LookupResult(query: "se despierta", entries: [form], lemmas: [despertar("to wake, wake up")])
        #expect(phrase.whoLines(language: .spanish, hint: "wakes up")
                == [WhoLine(subject: "él/ella/usted se despierta", english: "he/she wakes up")])
        // On its own, "despierta" can be a command.
        let word = LookupResult(query: "despierta", entries: [form], lemmas: [despertar("to wake, wake up")])
        #expect(word.whoLines(language: .spanish).contains(WhoLine(subject: "despierta", english: "wake!")))
    }
}
