import Foundation
import Testing
@testable import Mythos

@Suite("Delivery times")
struct DeliveryTimeTests {
    private func settings() -> AppSettings {
        AppSettings(defaults: UserDefaults(suiteName: "mythos-times-\(UUID().uuidString)")!)
    }

    @Test func eachCourseCanHaveItsOwnTime() throws {
        let s = settings()
        s.hour = 8
        s.minute = 0
        let spanish = try #require(s.add(.spanish, at: .a1)).id
        let greek = try #require(s.add(.greek, at: .a1)).id
        let seven = Calendar.current.date(bySettingHour: 7, minute: 0, second: 0, of: .now)!
        s.setDeliveryTime(seven, for: spanish)
        #expect(s.time(for: s.course(spanish)!) == (7, 0))
        #expect(s.time(for: s.course(greek)!) == (8, 0))
        #expect(s.hasOwnTime(spanish) && !s.hasOwnTime(greek))
        // Courses on the default follow it when it changes.
        s.hour = 9
        #expect(s.time(for: s.course(greek)!) == (9, 0))
        #expect(s.time(for: s.course(spanish)!) == (7, 0))
        s.useSharedTime(for: spanish)
        #expect(s.time(for: s.course(spanish)!) == (9, 0))
    }

    @Test func settingsSavedBeforePerLanguageTimesStillLoad() throws {
        let json = #"{"language":"el","level":"A1","startedOn":782000000}"#
        let course = try JSONDecoder().decode(Course.self, from: Data(json.utf8))
        #expect(course.language == .greek && course.hour == nil)
    }
}

@Suite("One language at several levels")
struct SeveralLevelsTests {
    private func settings(_ defaults: UserDefaults = UserDefaults(suiteName: "mythos-levels-\(UUID().uuidString)")!) -> AppSettings {
        AppSettings(defaults: defaults)
    }

    @Test func aLanguageCanBeLearnedAtTwoLevelsButNotTheSameOneTwice() throws {
        let s = settings()
        let a1 = try #require(s.add(.spanish, at: .a1))
        let a2 = try #require(s.add(.spanish, at: .a2))
        #expect(s.add(.spanish, at: .a1) == nil)
        #expect(s.courses.map(\.level) == [.a1, .a2])
        #expect(s.hasSeveralLevels(.spanish))
        #expect(!s.levelsToAdd(for: .spanish).contains(.a1) && s.levelsToAdd(for: .spanish).contains(.b1))
        // Moving A2 to A1 would duplicate it, so it stays; B1 is free.
        s.setLevel(.a1, for: a2.id)
        #expect(s.course(a2.id)?.level == .a2)
        s.setLevel(.b1, for: a2.id)
        #expect(s.course(a2.id)?.level == .b1 && s.course(a1.id)?.level == .a1)
    }

    @Test func eachLevelHasItsOwnStoryAndTime() throws {
        let s = settings()
        let a1 = try #require(s.add(.spanish, at: .a1))
        let b1 = try #require(s.add(.spanish, at: .b1))
        let story = try #require(DailyPlan(catalog: .shared).story(for: b1, on: .now))
        #expect(story.level == .b1 && s.course(for: story)?.id == b1.id)
        s.setDeliveryTime(Calendar.current.date(bySettingHour: 18, minute: 0, second: 0, of: .now)!, for: b1.id)
        #expect(!s.hasOwnTime(a1.id) && s.hasOwnTime(b1.id))
    }

    @Test func togglingALanguageOffRemovesEveryLevel() {
        let s = settings()
        s.add(.french, at: .a1)
        s.add(.french, at: .b2)
        s.add(.greek, at: .a1)
        s.toggle(.french)
        #expect(s.courses.map(\.language) == [.greek])
    }

    @Test func coursesSavedWithoutIDsKeepTheOnesTheyAreGiven() throws {
        let defaults = UserDefaults(suiteName: "mythos-levels-\(UUID().uuidString)")!
        // Settings as the first release saved them.
        let old = #"{"courses":[{"language":"es","level":"A2","startedOn":814000000,"hour":7,"minute":30}],"hour":8,"minute":0,"notificationsEnabled":true,"hasOnboarded":true,"speechRate":0.42}"#
        defaults.set(Data(old.utf8), forKey: "settings.v1")
        let first = settings(defaults)
        let course = try #require(first.courses.first)
        #expect(course.language == .spanish && course.level == .a2 && course.hour == 7 && first.hasOnboarded)
        #expect(course.startedOn == Date(timeIntervalSinceReferenceDate: 814000000))
        #expect(settings(defaults).courses.first?.id == course.id)
    }
}
