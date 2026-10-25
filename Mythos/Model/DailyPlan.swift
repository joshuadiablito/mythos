import Foundation

/// Which story each day brings. Deterministic, so notifications scheduled
/// weeks ahead name the same story the app shows on the day.
struct DailyPlan {
    let catalog: Catalog
    var calendar: Calendar = .current

    /// Days from `start` to `date`, by calendar day.
    private func days(from start: Date, to date: Date) -> Int {
        calendar.dateComponents([.day], from: calendar.startOfDay(for: start), to: calendar.startOfDay(for: date)).day ?? 0
    }

    /// A course's story for a day: its level's stories in order from the day
    /// the course started, starting again after the last.
    func story(for course: Course, on date: Date) -> Story? {
        let stories = catalog.stories(course.language, course.level)
        guard !stories.isEmpty else { return nil }
        let n = stories.count
        return stories[((days(from: course.startedOn, to: date) % n) + n) % n]
    }

    /// The story a day's notification features. With several languages, they take turns.
    func featured(in courses: [Course], on date: Date) -> Story? {
        let playable = courses.filter { catalog.hasStories($0.language, $0.level) }
        guard !playable.isEmpty else { return nil }
        let day = days(from: Date(timeIntervalSinceReferenceDate: 0), to: date)
        let course = playable[((day % playable.count) + playable.count) % playable.count]
        return story(for: course, on: date)
    }
}
