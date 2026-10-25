import Foundation
import Observation

/// A word the learner kept from a lookup.
struct SavedWord: Codable, Hashable, Identifiable {
    var id = UUID()
    let word: String
    let language: Language
    /// A short meaning, from the story's glossary or the dictionary.
    var meaning: String?
    let savedOn: Date
    /// The story it was found in.
    var storyTitle: String?
}

/// Stories read and words saved, kept in UserDefaults.
@Observable
final class ProgressStore {
    private struct Stored: Codable {
        var read: [String: Date]
        var savedWords: [SavedWord]
    }

    private static let key = "progress.v1"
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let calendar: Calendar

    /// Story ID → when it was finished.
    private(set) var read: [String: Date] { didSet { save() } }
    private(set) var savedWords: [SavedWord] { didSet { save() } }

    init(defaults: UserDefaults = .standard, calendar: Calendar = .current) {
        self.defaults = defaults
        self.calendar = calendar
        let stored = defaults.data(forKey: Self.key).flatMap { try? JSONDecoder().decode(Stored.self, from: $0) }
        read = stored?.read ?? [:]
        savedWords = stored?.savedWords ?? []
    }

    private func save() {
        if let data = try? JSONEncoder().encode(Stored(read: read, savedWords: savedWords)) {
            defaults.set(data, forKey: Self.key)
        }
    }

    // MARK: Reading

    func isRead(_ story: Story) -> Bool { read[story.id] != nil }

    func toggleRead(_ story: Story, on date: Date = .now) {
        read[story.id] = isRead(story) ? nil : date
    }

    func readCount(_ stories: [Story]) -> Int { stories.count { isRead($0) } }

    /// Consecutive days with a story finished, counting today or, if nothing
    /// is read yet today, ending yesterday (the streak isn't lost until tomorrow).
    func streak(asOf date: Date = .now) -> Int {
        let days = Set(read.values.map { calendar.startOfDay(for: $0) })
        var day = calendar.startOfDay(for: date)
        if !days.contains(day) { day = calendar.date(byAdding: .day, value: -1, to: day)! }
        var count = 0
        while days.contains(day) {
            count += 1
            day = calendar.date(byAdding: .day, value: -1, to: day)!
        }
        return count
    }

    // MARK: Words

    func isSaved(_ word: String, in language: Language) -> Bool {
        savedWords.contains { $0.language == language && $0.word.lowercased() == word.lowercased() }
    }

    func toggleSaved(_ word: String, in language: Language, meaning: String?, storyTitle: String?) {
        if let i = savedWords.firstIndex(where: { $0.language == language && $0.word.lowercased() == word.lowercased() }) {
            savedWords.remove(at: i)
        } else {
            savedWords.insert(SavedWord(word: word, language: language, meaning: meaning, savedOn: .now, storyTitle: storyTitle), at: 0)
        }
    }

    func remove(_ words: [SavedWord]) {
        let ids = Set(words.map(\.id))
        savedWords.removeAll { ids.contains($0.id) }
    }
}
