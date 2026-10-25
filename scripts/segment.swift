// Prints every story split into sentences exactly as the app splits them, so
// English translations can be written to match. Run from the repo root:
//   scripts/segment.sh [fr-a1]
import Foundation

let dir = URL(fileURLWithPath: "Mythos/Resources/Catalog")
let filter = CommandLine.arguments.dropFirst().first
for file in try FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil).sorted(by: { $0.path < $1.path }) {
    guard file.pathExtension == "json", filter.map({ file.lastPathComponent.contains($0) }) ?? true else { continue }
    let json = try JSONSerialization.jsonObject(with: Data(contentsOf: file)) as! [String: Any]
    let lang = json["language"] as! String
    for story in json["stories"] as! [[String: Any]] {
        print("## \(story["id"]!)")
        for (p, paragraph) in (story["paragraphs"] as! [String]).enumerated() {
            for sentence in SentenceSplitter.split(paragraph, languageCode: lang) { print("\(p + 1)| \(sentence)") }
        }
    }
}
