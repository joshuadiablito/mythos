import Foundation
import Observation

/// A language being learned, at a level. `startedOn` anchors the daily
/// rotation, so changing level starts that level from its first story.
struct Course: Codable, Hashable, Identifiable {
    var language: Language
    var level: Level
    var startedOn: Date
    /// When this language's story arrives, if not at the shared default time:
    /// Spanish at 7:00 and Greek at 8:00. Optional, so older settings decode.
    var hour: Int?
    var minute: Int?

    var id: Language { language }
}

/// When the day's story arrives.
enum DeliverySlot: String, Codable, CaseIterable, Identifiable {
    case morning, afternoon, evening

    var id: String { rawValue }

    var title: String { rawValue.capitalized }

    var symbol: String {
        switch self {
        case .morning: "sunrise.fill"
        case .afternoon: "sun.max.fill"
        case .evening: "moon.stars.fill"
        }
    }

    var defaultTime: DateComponents {
        switch self {
        case .morning: DateComponents(hour: 8, minute: 0)
        case .afternoon: DateComponents(hour: 13, minute: 0)
        case .evening: DateComponents(hour: 19, minute: 30)
        }
    }

    /// The slot a time of day falls in.
    static func containing(hour: Int) -> DeliverySlot {
        switch hour {
        case ..<12: .morning
        case ..<17: .afternoon
        default: .evening
        }
    }
}

/// The learner's choices, saved in UserDefaults.
@Observable
final class AppSettings {
    private struct Stored: Codable {
        var courses: [Course]
        var hour: Int
        var minute: Int
        var notificationsEnabled: Bool
        var hasOnboarded: Bool
        var speechRate: Double?
    }

    private static let key = "settings.v1"
    @ObservationIgnored private let defaults: UserDefaults

    var courses: [Course] { didSet { save() } }
    var hour: Int { didSet { save() } }
    var minute: Int { didSet { save() } }
    var notificationsEnabled: Bool { didSet { save() } }
    var hasOnboarded: Bool { didSet { save() } }
    /// Story read-aloud speed, 0.3 (slow) to 0.55 (natural).
    var speechRate: Double { didSet { save() } }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let stored = defaults.data(forKey: Self.key).flatMap { try? JSONDecoder().decode(Stored.self, from: $0) }
        courses = stored?.courses ?? []
        hour = stored?.hour ?? 8
        minute = stored?.minute ?? 0
        notificationsEnabled = stored?.notificationsEnabled ?? true
        hasOnboarded = stored?.hasOnboarded ?? false
        speechRate = stored?.speechRate ?? 0.42
    }

    private func save() {
        let stored = Stored(courses: courses, hour: hour, minute: minute, notificationsEnabled: notificationsEnabled,
                            hasOnboarded: hasOnboarded, speechRate: speechRate)
        if let data = try? JSONEncoder().encode(stored) { defaults.set(data, forKey: Self.key) }
    }

    var slot: DeliverySlot { DeliverySlot.containing(hour: hour) }

    func setSlot(_ slot: DeliverySlot) {
        hour = slot.defaultTime.hour ?? 8
        minute = slot.defaultTime.minute ?? 0
    }

    /// The delivery time as a date today, for DatePicker.
    var deliveryTime: Date {
        get { Calendar.current.date(bySettingHour: hour, minute: minute, second: 0, of: .now) ?? .now }
        set {
            let c = Calendar.current.dateComponents([.hour, .minute], from: newValue)
            hour = c.hour ?? hour
            minute = c.minute ?? minute
        }
    }

    func course(for language: Language) -> Course? { courses.first { $0.language == language } }

    /// When a course's story arrives: its own time, or the shared one.
    func time(for course: Course) -> (hour: Int, minute: Int) {
        (course.hour ?? hour, course.minute ?? minute)
    }

    /// A course's delivery time as a date today, for DatePicker.
    func deliveryTime(for language: Language) -> Date {
        let t = course(for: language).map(time(for:)) ?? (hour: hour, minute: minute)
        return Calendar.current.date(bySettingHour: t.hour, minute: t.minute, second: 0, of: .now) ?? .now
    }

    /// Gives one language its own delivery time.
    func setDeliveryTime(_ date: Date, for language: Language) {
        guard let i = courses.firstIndex(where: { $0.language == language }) else { return }
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        courses[i].hour = c.hour
        courses[i].minute = c.minute
    }

    /// Whether a language arrives at a time of its own rather than the shared one.
    func hasOwnTime(_ language: Language) -> Bool { course(for: language)?.hour != nil }

    /// Puts a language back on the shared time.
    func useSharedTime(for language: Language) {
        guard let i = courses.firstIndex(where: { $0.language == language }) else { return }
        courses[i].hour = nil
        courses[i].minute = nil
    }

    func toggle(_ language: Language) {
        if let i = courses.firstIndex(where: { $0.language == language }) {
            courses.remove(at: i)
        } else {
            courses.append(Course(language: language, level: .a1, startedOn: .now))
        }
    }

    func setLevel(_ level: Level, for language: Language) {
        guard let i = courses.firstIndex(where: { $0.language == language }), courses[i].level != level else { return }
        courses[i].level = level
        courses[i].startedOn = .now
    }
}
