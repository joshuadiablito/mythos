import Foundation
import Testing
@testable import Mythos

/// Real kaikki.org responses, shared with Paradigma's tests.
private final class FixtureBundle {}

private func fixture(_ name: String) throws -> [Entry] {
    let url = try #require(Bundle(for: FixtureBundle.self).url(forResource: name, withExtension: "jsonl"))
    return KaikkiParser.parse(try String(contentsOf: url, encoding: .utf8))
}

@Suite("kaikki.org parser")
struct KaikkiParserTests {
    @Test func urlsAreSegmentedByFirstLetters() {
        #expect(KaikkiParser.url(language: "French", word: "mange").absoluteString
                == "https://kaikki.org/dictionary/French/meaning/m/ma/mange.jsonl")
        #expect(KaikkiParser.url(language: "Greek", word: "τρώω").absoluteString
                == "https://kaikki.org/dictionary/Greek/meaning/%CF%84/%CF%84%CF%81/%CF%84%CF%81%CF%8E%CF%89.jsonl")
        #expect(KaikkiParser.url(language: "French", word: "s'habille").absoluteString
                == "https://kaikki.org/dictionary/French/meaning/s/s'/s'habille.jsonl")
    }

    @Test func anInflectedFormPointsToItsLemma() throws {
        let entries = try fixture("french-mange")
        let lemmas = Set(entries.flatMap(\.formOf).map(\.lemma))
        #expect(lemmas.contains("manger"))
        let description = try #require(entries.flatMap(\.formOf).first?.description)
        #expect(!description.hasSuffix("of manger"))
    }

    @Test func spanishPastIsAFormOfComer() throws {
        let entries = try fixture("spanish-comia")
        #expect(entries.flatMap(\.formOf).contains { $0.lemma == "comer" })
    }

    @Test func pronunciationRowsAreNotForms() throws {
        let entries = try fixture("german-haus")
        let forms = entries.flatMap(\.forms).map(\.form)
        #expect(!forms.isEmpty)
        #expect(forms.allSatisfy { !$0.contains("ʊ") && !$0.contains("ˈ") })
    }

    @Test func malformedLinesAreSkipped() {
        let entries = KaikkiParser.parse("not json\n{\"word\": \"x\"}\n")
        #expect(entries.isEmpty)
    }
}

@Suite("Inflection tables")
struct InflectionTests {
    @Test func germanNounDeclinesByCaseAndNumber() throws {
        let noun = try #require(try fixture("german-haus").first { $0.pos == "noun" })
        let inflections = InflectionBuilder.build(noun, language: .german)
        let table = try #require(inflections.tables.first)
        #expect(table.rowHeading == "Case")
        #expect(table.rows.map(\.key).contains("dative"))
        #expect(table.contains("Häuser"))
    }

    @Test func greekVerbHasNamedTensesWithPronouns() throws {
        let verb = try #require(try fixture("greek-troo").first { $0.pos == "verb" && !$0.forms.isEmpty })
        let inflections = InflectionBuilder.build(verb, language: .greek)
        let titles = inflections.tables.map(\.title)
        #expect(titles.contains("Active present"))
        #expect(titles.contains("Active simple past (aorist)"))
        #expect(titles.contains("Passive present"))
        let present = try #require(inflections.tables.first { $0.title == "Active present" })
        #expect(present.cells["first-person"]?["singular"]?.pronoun == "εγώ")
    }

    @Test func spanishAdjectiveIncludesItsLemma() throws {
        let adjective = try #require(try fixture("spanish-bonito").first { $0.pos == "adj" })
        let inflections = InflectionBuilder.build(adjective, language: .spanish)
        #expect(inflections.tables.contains { $0.contains("bonito") && $0.contains("bonitas") })
    }

    @Test func frenchJeElidesBeforeAVowel() throws {
        let entry = Entry(word: "aimer", pos: "verb", head: nil, ipa: nil, audioURL: nil, senses: [], forms: [
            WordForm(form: "aime", tags: ["first-person", "singular", "present", "indicative"], fromTable: true),
            WordForm(form: "aimons", tags: ["first-person", "plural", "present", "indicative"], fromTable: true),
        ], formOf: [])
        let table = try #require(InflectionBuilder.build(entry, language: .french).tables.first)
        #expect(table.cells["first-person"]?["singular"]?.pronoun == "j’")
        #expect(table.cells["first-person"]?["plural"]?.pronoun == "nous")
        #expect(table.title == "Indicative present")
    }
}

@Suite("Lookup candidates")
struct LookupCandidateTests {
    @Test func casesAndElisionsAreTried() {
        #expect(LookupCandidates.candidates(for: "Mange") == ["Mange", "mange"])
        #expect(LookupCandidates.candidates(for: "l’école") == ["l'école", "école", "L'école", "École"])
        #expect(LookupCandidates.candidates(for: "haus").contains("Haus"))
        #expect(LookupCandidates.candidates(for: "  ").isEmpty)
    }
}
