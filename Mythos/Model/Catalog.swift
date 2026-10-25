import Foundation
import OSLog

/// Every story, bundled with the app as `catalog-<language>-<level>.json`.
/// Nothing is downloaded: the catalog ships with each release.
final class Catalog {
    static let shared = Catalog(bundle: .main)

    private(set) var stories: [Story] = []
    private var byID: [String: Story] = [:]
    private var byCourse: [String: [Story]] = [:]

    init(bundle: Bundle) {
        let urls = (bundle.urls(forResourcesWithExtension: "json", subdirectory: nil) ?? [])
            .filter { $0.lastPathComponent.hasPrefix("catalog-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
        let decoder = JSONDecoder()
        for url in urls {
            do {
                let file = try decoder.decode(CatalogFile.self, from: Data(contentsOf: url))
                let stories = file.resolved
                self.stories += stories
                byCourse[Self.key(file.language, file.level)] = stories
            } catch {
                Logger(subsystem: "Mythos", category: "Catalog").error("Couldn't read \(url.lastPathComponent): \(error)")
            }
        }
        byID = Dictionary(stories.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }

    private static func key(_ language: Language, _ level: Level) -> String { "\(language.rawValue)-\(level.rawValue)" }

    func stories(_ language: Language, _ level: Level) -> [Story] {
        byCourse[Self.key(language, level)] ?? []
    }

    func story(id: String) -> Story? { byID[id] }

    func hasStories(_ language: Language, _ level: Level) -> Bool { !stories(language, level).isEmpty }
}
