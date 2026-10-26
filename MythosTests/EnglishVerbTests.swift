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
