import Foundation
import Testing
@testable import Mythos

/// Answers kaikki.org requests from fixtures, counting them.
private final class StubProtocol: URLProtocol {
    nonisolated(unsafe) static var requests: [URL] = []
    nonisolated(unsafe) static var responses: [String: (Int, Data)] = [:]

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let url = request.url!
        Self.requests.append(url)
        let (status, data) = Self.responses[url.lastPathComponent] ?? (404, Data())
        client?.urlProtocol(self, didReceive: HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil)!,
                            cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

private final class FixtureBundle {}

@Suite("Dictionary cache", .serialized)
final class DictionaryCacheTests {
    /// Each test's own cache directory, removed when the test ends.
    private let directory = FileManager.default.temporaryDirectory.appending(path: "cache-\(UUID().uuidString)")

    deinit { try? FileManager.default.removeItem(at: directory) }

    private func temporaryCache(maxBytes: Int = 80 << 20) -> DictionaryCache {
        DictionaryCache(directory: directory, maxBytes: maxBytes)
    }

    private func stubbedSession() throws -> URLSession {
        let url = try #require(Bundle(for: FixtureBundle.self).url(forResource: "french-mange", withExtension: "jsonl"))
        StubProtocol.requests = []
        StubProtocol.responses = ["mange.jsonl": (200, try Data(contentsOf: url)),
                                  "manger.jsonl": (200, Data(#"{"word": "manger", "pos": "verb", "senses": [{"glosses": ["to eat"]}]}"#.utf8))]
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubProtocol.self]
        return URLSession(configuration: config)
    }

    @Test func storesAndReadsResponsesAndMissingWords() {
        let cache = temporaryCache()
        let url = URL(string: "https://kaikki.org/dictionary/French/meaning/m/ma/mange.jsonl")!
        #expect(cache.read(url) == nil)
        cache.store(.found(Data("x".utf8)), for: url)
        #expect(cache.read(url) == .found(Data("x".utf8)))

        let missing = URL(string: "https://kaikki.org/dictionary/French/meaning/z/zz/zzz.jsonl")!
        cache.store(.notFound, for: missing)
        #expect(cache.read(missing) == .notFound)
        // "No such word" is forgotten after 30 days, in case Wiktionary has added it.
        #expect(cache.read(missing, now: .now.addingTimeInterval(31 * 24 * 3600)) == nil)
    }

    @Test func trimmingRemovesTheLeastRecentlyUsed() {
        let cache = temporaryCache(maxBytes: 150)
        let old = URL(string: "https://kaikki.org/a.jsonl")!, recent = URL(string: "https://kaikki.org/b.jsonl")!
        cache.store(.found(Data(repeating: 1, count: 100)), for: old)
        cache.store(.found(Data(repeating: 2, count: 100)), for: recent)
        _ = cache.read(old, now: .now.addingTimeInterval(-3600))
        _ = cache.read(recent, now: .now)
        cache.trim()
        #expect(cache.read(old) == nil)
        #expect(cache.read(recent) != nil)
    }

    @Test func aRepeatLookupMakesNoRequests() async throws {
        let cache = temporaryCache()
        let first = DictionaryClient(session: try stubbedSession(), cache: cache)
        let result = try await first.lookUp("Mange", in: .french)
        #expect(result.lemmas.contains { $0.word == "manger" })
        // "Mange" (404), "mange", then the lemma "manger".
        #expect(StubProtocol.requests.map(\.lastPathComponent) == ["Mange.jsonl", "mange.jsonl", "manger.jsonl"])

        // A new client (as after relaunching the app) answers entirely from disk.
        StubProtocol.requests = []
        let second = DictionaryClient(session: try stubbedSession(), cache: cache)
        let again = try await second.lookUp("Mange", in: .french)
        #expect(StubProtocol.requests.isEmpty)
        #expect(again.lemmas.map(\.word) == result.lemmas.map(\.word))
    }
}
