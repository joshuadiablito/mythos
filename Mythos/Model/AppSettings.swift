import Foundation
import Observation

/// A language being learned, at a level. `startedOn` anchors the daily
/// rotation, so changing level starts that level from its first story.
/// A language can be learned at several levels at once (Spanish A1 and A2),
/// but not twice at the same one.
struct Course: Codable, Hashable, Identifiable {
    /// Stable while the level changes, so a course can be edited in place.
    var id = UUID()
    var language: Language
    var level: Level
    var startedOn: Date
    /// When this course's story arrives, if not at the shared default time:
    /// Spanish at 7:00 and Greek at 8:00. Optional, so older settings decode.
    var hour: Int?
    var minute: Int?
}

extension Course {
    private enum CodingKeys: String, CodingKey { case id, language, level, startedOn, hour, minute }

    /// Settings saved before courses had IDs get a new one.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        language = try c.decode(Language.self, forKey: .language)
        level = try c.decode(Level.self, forKey: .level)
        startedOn = try c.decode(Date.self, forKey: .startedOn)
        hour = try c.decodeIfPresent(Int.self, forKey: .hour)
        minute = try c.decodeIfPresent(Int.self, forKey: .minute)
    }
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
        // Keep the IDs given to courses saved without them.
        if stored != nil { save() }
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

    func course(_ id: Course.ID) -> Course? { courses.first { $0.id == id } }

    /// The course a story belongs to.
    func course(for story: Story) -> Course? {
        courses.first { $0.language == story.language && $0.level == story.level }
    }

    func isLearning(_ language: Language) -> Bool { courses.contains { $0.language == language } }

    /// The levels a language could still be added at.
    func levelsToAdd(for language: Language) -> [Level] {
        Level.allCases.filter { level in level.isAvailable && !courses.contains { $0.language == language && $0.level == level } }
    }

    /// When a course's story arrives: its own time, or the shared one.
    func time(for course: Course) -> (hour: Int, minute: Int) {
        (course.hour ?? hour, course.minute ?? minute)
    }

    /// A course's delivery time as a date today, for DatePicker; with no
    /// course, the shared time.
    func deliveryTime(for id: Course.ID?) -> Date {
        let t = id.flatMap(course).map(time(for:)) ?? (hour: hour, minute: minute)
        return Calendar.current.date(bySettingHour: t.hour, minute: t.minute, second: 0, of: .now) ?? .now
    }

    /// Gives one course its own delivery time.
    func setDeliveryTime(_ date: Date, for id: Course.ID) {
        guard let i = courses.firstIndex(where: { $0.id == id }) else { return }
        let c = Calendar.current.dateComponents([.hour, .minute], from: date)
        courses[i].hour = c.hour
        courses[i].minute = c.minute
    }

    /// Whether a course arrives at a time of its own rather than the shared one.
    func hasOwnTime(_ id: Course.ID) -> Bool { course(id)?.hour != nil }

    /// Puts a course back on the shared time.
    func useSharedTime(for id: Course.ID) {
        guard let i = courses.firstIndex(where: { $0.id == id }) else { return }
        courses[i].hour = nil
        courses[i].minute = nil
    }

    /// Starts or stops learning a language: at A1, or every level of it.
    func toggle(_ language: Language) {
        if isLearning(language) {
            courses.removeAll { $0.language == language }
        } else {
            add(language, at: .a1)
        }
    }

    /// Learns a language at another level, unless it's already learned at that one.
    @discardableResult
    func add(_ language: Language, at level: Level) -> Course? {
        guard !courses.contains(where: { $0.language == language && $0.level == level }) else { return nil }
        let course = Course(language: language, level: level, startedOn: .now)
        courses.append(course)
        return course
    }

    /// Changes a course's level, unless the language is already learned at that one.
    func setLevel(_ level: Level, for id: Course.ID) {
        guard let i = courses.firstIndex(where: { $0.id == id }), courses[i].level != level,
              !courses.contains(where: { $0.language == courses[i].language && $0.level == level }) else { return }
        courses[i].level = level
        courses[i].startedOn = .now
    }

    /// Whether more than one course is in this language, so its level is
    /// needed to tell them apart.
    func hasSeveralLevels(_ language: Language) -> Bool {
        courses.count { $0.language == language } > 1
    }
}
