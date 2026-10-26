import Foundation
import Testing
@testable import Mythos

@Suite("Delivery times")
struct DeliveryTimeTests {
    private func settings() -> AppSettings {
        AppSettings(defaults: UserDefaults(suiteName: "mythos-times-\(UUID().uuidString)")!)
    }

    @Test func eachLanguageCanHaveItsOwnTime() {
        let s = settings()
        s.hour = 8
        s.minute = 0
        s.toggle(.spanish)
        s.toggle(.greek)
        let seven = Calendar.current.date(bySettingHour: 7, minute: 0, second: 0, of: .now)!
        s.setDeliveryTime(seven, for: .spanish)
        #expect(s.time(for: s.course(for: .spanish)!) == (7, 0))
        #expect(s.time(for: s.course(for: .greek)!) == (8, 0))
        #expect(s.hasOwnTime(.spanish) && !s.hasOwnTime(.greek))
        // Languages on the default follow it when it changes.
        s.hour = 9
        #expect(s.time(for: s.course(for: .greek)!) == (9, 0))
        #expect(s.time(for: s.course(for: .spanish)!) == (7, 0))
        s.useSharedTime(for: .spanish)
        #expect(s.time(for: s.course(for: .spanish)!) == (9, 0))
    }

    @Test func settingsSavedBeforePerLanguageTimesStillLoad() throws {
        let json = #"{"language":"el","level":"A1","startedOn":782000000}"#
        let course = try JSONDecoder().decode(Course.self, from: Data(json.utf8))
        #expect(course.language == .greek && course.hour == nil)
    }
}
