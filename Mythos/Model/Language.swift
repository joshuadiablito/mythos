import Foundation

/// A language Mythos has a story catalog for. Brazilian and European
/// Portuguese are separate: their stories, spelling and voices differ.
enum Language: String, CaseIterable, Codable, Identifiable, Hashable, Sendable {
    case french = "fr"
    case spanish = "es"
    case german = "de"
    case italian = "it"
    /// Brazilian Portuguese. Its code stays "pt" so saved courses keep working.
    case portuguese = "pt"
    case portugal = "pt-PT"
    case greek = "el"

    var id: String { rawValue }

    /// The language's name in English, as shown to the learner.
    var name: String {
        switch self {
        case .french: "French"
        case .spanish: "Spanish"
        case .german: "German"
        case .italian: "Italian"
        case .portuguese: "Portuguese (Brazil)"
        case .portugal: "Portuguese (Portugal)"
        case .greek: "Greek"
        }
    }

    /// The name kaikki.org files the language under. Both Portuguese
    /// variants share Wiktionary's one Portuguese dictionary.
    var dictionaryName: String {
        switch self {
        case .portuguese, .portugal: "Portuguese"
        default: name
        }
    }

    /// The ISO 639-1 code, without a region: both Portuguese variants are "pt".
    var baseCode: String { String(rawValue.prefix(2)) }

    /// The language's name for itself.
    var endonym: String {
        switch self {
        case .french: "Français"
        case .spanish: "Español"
        case .german: "Deutsch"
        case .italian: "Italiano"
        case .portuguese: "Português (Brasil)"
        case .portugal: "Português (Portugal)"
        case .greek: "Ελληνικά"
        }
    }

    /// Two letters for the language's badge.
    var monogram: String {
        switch self {
        case .greek: "Ελ"
        case .portuguese: "Br"
        case .portugal: "Pt"
        default: rawValue.capitalized
        }
    }

    /// Voice used to read stories aloud.
    var voiceCode: String {
        switch self {
        case .french: "fr-FR"
        case .spanish: "es-ES"
        case .german: "de-DE"
        case .italian: "it-IT"
        case .portuguese: "pt-BR"
        case .portugal: "pt-PT"
        case .greek: "el-GR"
        }
    }

    var locale: Locale.Language { Locale.Language(identifier: voiceCode) }

    /// "Hello" in the language, for onboarding.
    var greeting: String {
        switch self {
        case .french: "Bonjour"
        case .spanish: "Hola"
        case .german: "Hallo"
        case .italian: "Ciao"
        case .portuguese, .portugal: "Olá"
        case .greek: "Γεια σου"
        }
    }

    /// "Good morning", "Good afternoon" or "Good evening" in the language.
    func greeting(at date: Date, calendar: Calendar = .current) -> String {
        let hour = calendar.component(.hour, from: date)
        let part = hour < 12 ? 0 : hour < 18 ? 1 : 2
        let greetings: [String] = switch self {
        case .french: ["Bonjour", "Bon après-midi", "Bonsoir"]
        case .spanish: ["Buenos días", "Buenas tardes", "Buenas noches"]
        case .german: ["Guten Morgen", "Guten Tag", "Guten Abend"]
        case .italian: ["Buongiorno", "Buon pomeriggio", "Buonasera"]
        case .portuguese, .portugal: ["Bom dia", "Boa tarde", "Boa noite"]
        case .greek: ["Καλημέρα", "Καλό απόγευμα", "Καλησπέρα"]
        }
        return greetings[part]
    }

    /// Today's date as the language writes it: "dimanche 25 octobre".
    func longDate(_ date: Date) -> String {
        date.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Locale(identifier: voiceCode)))
    }
}
