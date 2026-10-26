import Foundation

/// A CEFR level, from A1 (beginner) to C2 (proficient).
enum Level: String, CaseIterable, Codable, Identifiable, Hashable, Sendable {
    case a1 = "A1", a2 = "A2", b1 = "B1", b2 = "B2", c1 = "C1", c2 = "C2"

    var id: String { rawValue }

    /// Every level has a catalog in every language.
    var isAvailable: Bool { true }

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
        case .b1: "Longer stories, opinions, plans and what might have been."
        case .b2: "Arguments, reported speech and the subjunctive in earnest."
        case .c1: "Nuance, idiom and feature-length writing."
        case .c2: "Literary style, irony and rare words: as a native reads."
        }
    }
}
