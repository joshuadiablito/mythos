import Foundation

enum DictionaryError: LocalizedError {
    case unavailable(status: Int)

    var errorDescription: String? {
        switch self {
        case .unavailable(let status): "The dictionary isn't responding (kaikki.org returned \(status)). Try again in a moment."
        }
    }
}

/// Looks words up on kaikki.org: free, keyless, and cached on disk, so a word
/// looked up once is there offline, and the same word costs no second request.
final class DictionaryClient {
    static let shared = DictionaryClient()

    private static let maxLemmas = 3

    private let session: URLSession
    private var memory: [String: LookupResult] = [:]

    init(session: URLSession? = nil) {
        if let session {
            self.session = session
        } else {
            let config = URLSessionConfiguration.default
            config.urlCache = URLCache(memoryCapacity: 8 << 20, diskCapacity: 100 << 20, directory: nil)
            // Dictionary entries rarely change; prefer the cache over the network.
            config.requestCachePolicy = .returnCacheDataElseLoad
            config.timeoutIntervalForRequest = 15
            self.session = URLSession(configuration: config)
        }
    }

    /// Looks a word up under each plausible spelling until one is found, then
    /// fetches the lemmas it is a form of ("mange" → "manger").
    func lookUp(_ text: String, in language: Language) async throws -> LookupResult {
        let key = "\(language.rawValue)|\(text)"
        if let cached = memory[key] { return cached }

        var entries: [Entry] = []
        for candidate in LookupCandidates.candidates(for: text) {
            entries = try await fetch(candidate, language: language.name)
            if !entries.isEmpty { break }
        }
        let own = Set(entries.map(\.word))
        var lemmaWords: [String] = []
        for lemma in entries.flatMap(\.formOf).map(\.lemma) where !own.contains(lemma) && !lemmaWords.contains(lemma) {
            lemmaWords.append(lemma)
        }
        // A lemma that fails to load loses its table, not the lookup.
        let lemmas = await withTaskGroup(of: (Int, [Entry]).self) { group in
            for (i, word) in lemmaWords.prefix(Self.maxLemmas).enumerated() {
                group.addTask { (i, (try? await self.fetch(word, language: language.name)) ?? []) }
            }
            var results: [(Int, [Entry])] = []
            for await r in group { results.append(r) }
            return results.sorted { $0.0 < $1.0 }.flatMap(\.1)
        }

        let result = LookupResult(query: text, entries: entries, lemmas: lemmas)
        memory[key] = result
        return result
    }

    /// One spelling. A 404 means "no such word", not an error.
    private func fetch(_ word: String, language: String) async throws -> [Entry] {
        let (data, response) = try await session.data(from: KaikkiParser.url(language: language, word: word))
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if status == 404 { return [] }
        guard (200..<300).contains(status) else { throw DictionaryError.unavailable(status: status) }
        return await Self.parse(data)
    }

    // Verb entries run to hundreds of kilobytes; parse them off the main actor.
    @concurrent
    private nonisolated static func parse(_ data: Data) async -> [Entry] {
        KaikkiParser.parse(String(decoding: data, as: UTF8.self))
    }
}
