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

    /// The days of the week containing `date`, each with whether a story was finished on it.
    func week(containing date: Date = .now) -> [(day: Date, read: Bool)] {
        let readDays = Set(read.values.map { calendar.startOfDay(for: $0) })
        guard let start = calendar.dateInterval(of: .weekOfYear, for: date)?.start else { return [] }
        return (0..<7).compactMap { offset in
            calendar.date(byAdding: .day, value: offset, to: start).map { ($0, readDays.contains(calendar.startOfDay(for: $0))) }
        }
    }

    // MARK: Words

    func isSaved(_ word: String, in language: Language) -> Bool {
        savedWords.contains { $0.language == language && $0.word.lowercased() == word.lowercased() }
    }

    /// Saves each of a story's glossary words that isn't saved already.
    func saveAll(_ items: [VocabItem], in language: Language, storyTitle: String) {
        for item in items.reversed() where !isSaved(item.word, in: language) {
            savedWords.insert(SavedWord(word: item.word, language: language, meaning: item.gloss, savedOn: .now, storyTitle: storyTitle), at: 0)
        }
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
