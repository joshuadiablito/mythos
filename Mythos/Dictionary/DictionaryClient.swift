import Foundation

enum DictionaryError: LocalizedError {
    case unavailable(status: Int)

    var errorDescription: String? {
        switch self {
        case .unavailable(let status): "The dictionary isn't responding (kaikki.org returned \(status)). Try again in a moment."
        }
    }
}

/// Looks words up on kaikki.org: free and keyless. Every response, including
/// "no such word", is kept in a `DictionaryCache` that is checked first, so a
/// word looked up once is there offline and never costs a second request.
final class DictionaryClient {
    static let shared = DictionaryClient()

    private static let maxLemmas = 3

    private let session: URLSession
    let cache: DictionaryCache
    private var memory: [String: LookupResult] = [:]

    init(session: URLSession? = nil, cache: DictionaryCache = DictionaryCache()) {
        self.cache = cache
        if let session {
            self.session = session
        } else {
            let config = URLSessionConfiguration.default
            // Our own cache keeps responses; URLCache would only duplicate them.
            config.urlCache = nil
            config.requestCachePolicy = .reloadIgnoringLocalCacheData
            config.timeoutIntervalForRequest = 15
            self.session = URLSession(configuration: config)
        }
        Task.detached(priority: .background) { [cache] in cache.trim() }
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

    /// One spelling: from the cache if it's there, otherwise from kaikki.org,
    /// then cached. A 404 means "no such word", not an error, and is cached too.
    private func fetch(_ word: String, language: String) async throws -> [Entry] {
        let url = KaikkiParser.url(language: language, word: word)
        switch cache.read(url) {
        case .found(let data): return await Self.parse(data)
        case .notFound: return []
        case nil: break
        }
        let (data, response) = try await session.data(from: url)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if status == 404 {
            cache.store(.notFound, for: url)
            return []
        }
        guard (200..<300).contains(status) else { throw DictionaryError.unavailable(status: status) }
        cache.store(.found(data), for: url)
        return await Self.parse(data)
    }

    // Verb entries run to hundreds of kilobytes; parse them off the main actor.
    @concurrent
    private nonisolated static func parse(_ data: Data) async -> [Entry] {
        KaikkiParser.parse(String(decoding: data, as: UTF8.self))
    }
}
