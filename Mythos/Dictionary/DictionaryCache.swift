import CryptoKit
import Foundation

/// kaikki.org responses kept on disk, checked before every request. A word
/// looked up once never costs another request, and works offline.
///
/// Kept in Application Support rather than Caches, so iOS doesn't clear it
/// under storage pressure; it trims itself instead, least recently used first.
/// It is excluded from backups, since every file in it can be downloaded again.
/// "No such word" answers are remembered too, for 30 days, since Wiktionary
/// does add pages.
nonisolated final class DictionaryCache: Sendable {
    enum Hit: Equatable {
        case found(Data)
        case notFound
    }

    private let directory: URL
    private let maxBytes: Int
    private let notFoundLifetime: TimeInterval = 30 * 24 * 3600

    init(directory: URL? = nil, maxBytes: Int = 80 << 20) {
        self.directory = directory ?? URL.applicationSupportDirectory.appending(path: "DictionaryCache", directoryHint: .isDirectory)
        self.maxBytes = maxBytes
        createDirectory()
    }

    private func createDirectory() {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var directory = directory
        try? directory.setResourceValues(values)
    }

    private func key(_ url: URL) -> String {
        SHA256.hash(data: Data(url.absoluteString.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private func foundFile(_ url: URL) -> URL { directory.appending(path: key(url) + ".jsonl") }
    private func notFoundFile(_ url: URL) -> URL { directory.appending(path: key(url) + ".404") }

    func read(_ url: URL, now: Date = .now) -> Hit? {
        let fm = FileManager.default
        let found = foundFile(url)
        if let data = try? Data(contentsOf: found) {
            // Mark it recently used, for trimming.
            try? fm.setAttributes([.modificationDate: now], ofItemAtPath: found.path)
            return .found(data)
        }
        let missing = notFoundFile(url)
        if let date = (try? fm.attributesOfItem(atPath: missing.path))?[.modificationDate] as? Date {
            if now.timeIntervalSince(date) < notFoundLifetime { return .notFound }
            try? fm.removeItem(at: missing)
        }
        return nil
    }

    func store(_ hit: Hit, for url: URL) {
        switch hit {
        case .found(let data): try? data.write(to: foundFile(url), options: .atomic)
        case .notFound: try? Data().write(to: notFoundFile(url), options: .atomic)
        }
    }

    /// Removes the least recently used entries until the cache fits its budget.
    func trim() {
        let fm = FileManager.default
        let keys: [URLResourceKey] = [.fileSizeKey, .contentModificationDateKey]
        guard let files = try? fm.contentsOfDirectory(at: directory, includingPropertiesForKeys: keys) else { return }
        let entries = files.compactMap { file -> (URL, Int, Date)? in
            guard let values = try? file.resourceValues(forKeys: Set(keys)) else { return nil }
            return (file, values.fileSize ?? 0, values.contentModificationDate ?? .distantPast)
        }
        var total = entries.reduce(0) { $0 + $1.1 }
        for (file, size, _) in entries.sorted(by: { $0.2 < $1.2 }) where total > maxBytes {
            try? fm.removeItem(at: file)
            total -= size
        }
    }

    func removeAll() {
        try? FileManager.default.removeItem(at: directory)
        createDirectory()
    }

    var sizeInBytes: Int {
        let files = (try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.fileSizeKey])) ?? []
        return files.reduce(0) { $0 + ((try? $1.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0) }
    }
}
