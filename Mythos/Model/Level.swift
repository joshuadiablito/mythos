import Foundation

/// A CEFR level. Only some have stories yet; the rest are shown as coming soon.
enum Level: String, CaseIterable, Codable, Identifiable, Hashable, Sendable {
    case a1 = "A1", a2 = "A2", b1 = "B1", b2 = "B2", c1 = "C1", c2 = "C2"

    var id: String { rawValue }

    var isAvailable: Bool { self == .a1 || self == .a2 }

    var title: String {
        switch self {
        case .a1: "Beginner"
        case .a2: "Elementary"
        case .b1: "Intermediate"
        case .b2: "Upper intermediate"
        case .c1: "Advanced"
        case .c2: "Proficient"
        }
    }

    var summary: String {
        switch self {
        case .a1: "Everyday words, the present tense, short sentences."
        case .a2: "Past and future, routines, small stories about people."
        case .b1: "Opinions, plans and longer stories."
        case .b2: "News, arguments and natural dialogue."
        case .c1: "Nuance, idiom and long-form writing."
        case .c2: "Anything a native reader would pick up."
        }
    }
}
