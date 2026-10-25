import Foundation
import Testing
import UIKit
@testable import Mythos

@Suite("Catalog")
struct CatalogTests {
    let catalog = Catalog(bundle: Bundle(for: AppDelegate.self))

    @Test("Every language has 15 stories at A1 and at A2", arguments: Language.allCases)
    func storiesPerLevel(language: Language) {
        #expect(catalog.stories(language, .a1).count == 15)
        #expect(catalog.stories(language, .a2).count == 15)
        #expect(catalog.stories(language, .b1).isEmpty)
    }

    @Test func idsAreUniqueAndNamedForTheirCourse() {
        #expect(Set(catalog.stories.map(\.id)).count == catalog.stories.count)
        for story in catalog.stories {
            #expect(story.id.hasPrefix("\(story.language.rawValue)-\(story.level.rawValue.lowercased())-"), "\(story.id)")
        }
    }

    @Test("Each glossary word appears in its story, as written", arguments: Language.allCases)
    func vocabularyIsInTheText(language: Language) {
        for story in catalog.stories.filter({ $0.language == language }) {
            let text = story.text.lowercased()
            #expect(story.vocab.count >= 6, "\(story.id) has only \(story.vocab.count) words")
            for item in story.vocab {
                #expect(text.contains(item.word.lowercased()), "\(story.id): “\(item.word)” isn't in the text")
            }
        }
    }

    @Test func storiesHaveTwoOrThreeParagraphs() {
        for story in catalog.stories {
            #expect((2...3).contains(story.paragraphs.count), "\(story.id)")
            #expect(story.paragraphs.allSatisfy { $0.count > 80 }, "\(story.id) has a very short paragraph")
        }
    }

    @Test func symbolsExist() {
        for symbol in Set(catalog.stories.map(\.symbol)) {
            #expect(UIImage(systemName: symbol) != nil, "No SF Symbol named \(symbol)")
        }
    }

    @Test func openingIsTheFirstSentence() throws {
        let story = try #require(catalog.story(id: "fr-a1-01"))
        #expect(story.opening == "Bonjour !")
        let german = try #require(catalog.story(id: "de-a1-02"))
        #expect(german.opening == "Felix steht um sieben Uhr auf.")
    }
}

@Suite("Daily plan")
struct DailyPlanTests {
    let catalog = Catalog(bundle: Bundle(for: AppDelegate.self))
    var calendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Europe/London")!
        return c
    }

    private func day(_ offset: Int, from start: Date) -> Date {
        calendar.date(byAdding: .day, value: offset, to: start)!
    }

    @Test func coursesStartAtTheirFirstStoryAndWrapAround() {
        let start = calendar.date(from: DateComponents(year: 2026, month: 10, day: 1, hour: 9))!
        let course = Course(language: .french, level: .a1, startedOn: start)
        let plan = DailyPlan(catalog: catalog, calendar: calendar)
        #expect(plan.story(for: course, on: start)?.id == "fr-a1-01")
        #expect(plan.story(for: course, on: day(1, from: start).addingTimeInterval(-8 * 3600))?.id == "fr-a1-02")
        #expect(plan.story(for: course, on: day(14, from: start))?.id == "fr-a1-15")
        #expect(plan.story(for: course, on: day(15, from: start))?.id == "fr-a1-01")
    }

    @Test func notificationsTakeTurnsBetweenLanguages() {
        let start = calendar.date(from: DateComponents(year: 2026, month: 10, day: 1))!
        let courses = [Course(language: .spanish, level: .a1, startedOn: start), Course(language: .greek, level: .a2, startedOn: start)]
        let plan = DailyPlan(catalog: catalog, calendar: calendar)
        let languages = (0..<4).compactMap { plan.featured(in: courses, on: day($0, from: start))?.language }
        #expect(languages.count == 4)
        #expect(languages[0] != languages[1])
        #expect(languages[0] == languages[2])
    }

    @Test func coursesWithoutStoriesAreSkipped() {
        let start = Date.now
        let plan = DailyPlan(catalog: catalog, calendar: calendar)
        #expect(plan.featured(in: [Course(language: .italian, level: .b2, startedOn: start)], on: start) == nil)
        let mixed = [Course(language: .italian, level: .b2, startedOn: start), Course(language: .german, level: .a2, startedOn: start)]
        #expect(plan.featured(in: mixed, on: start)?.language == .german)
    }
}

@Suite("Progress")
struct ProgressTests {
    private func store() -> ProgressStore {
        let defaults = UserDefaults(suiteName: "mythos-tests-\(UUID().uuidString)")!
        return ProgressStore(defaults: defaults, calendar: Calendar(identifier: .gregorian))
    }

    @Test func streakCountsConsecutiveDaysAndSurvivesUntilTomorrow() {
        let progress = store()
        let catalog = Catalog(bundle: Bundle(for: AppDelegate.self))
        let stories = catalog.stories(.french, .a1)
        let today = Date.now
        let cal = Calendar(identifier: .gregorian)
        #expect(progress.streak(asOf: today) == 0)
        progress.toggleRead(stories[0], on: cal.date(byAdding: .day, value: -2, to: today)!)
        progress.toggleRead(stories[1], on: cal.date(byAdding: .day, value: -1, to: today)!)
        #expect(progress.streak(asOf: today) == 2)
        progress.toggleRead(stories[2], on: today)
        #expect(progress.streak(asOf: today) == 3)
        #expect(progress.streak(asOf: cal.date(byAdding: .day, value: 2, to: today)!) == 0)
    }

    @Test func savingIsCaseInsensitivePerLanguage() {
        let progress = store()
        progress.toggleSaved("Maison", in: .french, meaning: "house", storyTitle: nil)
        #expect(progress.isSaved("maison", in: .french))
        #expect(!progress.isSaved("maison", in: .spanish))
        progress.toggleSaved("maison", in: .french, meaning: nil, storyTitle: nil)
        #expect(progress.savedWords.isEmpty)
    }
}

@Suite("Notifications")
struct NotificationContentTests {
    @Test func notificationsInviteTheReaderIn() throws {
        let story = try #require(Catalog(bundle: Bundle(for: AppDelegate.self)).story(id: "fr-a1-01"))
        let content = NotificationScheduler.content(for: story)
        #expect(content.title == "Bonjour! Your new story is here")
        #expect(content.subtitle == "Je m'appelle Léa · Français A1")
        #expect(content.body.hasPrefix("A new story is ready for you to read today."))
        #expect(content.userInfo[NotificationScheduler.storyIDKey] as? String == "fr-a1-01")
    }
}
