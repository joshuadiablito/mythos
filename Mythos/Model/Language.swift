import Foundation

/// A language Mythos has a story catalog for.
enum Language: String, CaseIterable, Codable, Identifiable, Hashable, Sendable {
    case french = "fr"
    case spanish = "es"
    case german = "de"
    case italian = "it"
    case portuguese = "pt"
    case greek = "el"

    var id: String { rawValue }

    /// English name, as used in kaikki.org URLs.
    var name: String {
        switch self {
        case .french: "French"
        case .spanish: "Spanish"
        case .german: "German"
        case .italian: "Italian"
        case .portuguese: "Portuguese"
        case .greek: "Greek"
        }
    }

    /// The language's name for itself.
    var endonym: String {
        switch self {
        case .french: "Français"
        case .spanish: "Español"
        case .german: "Deutsch"
        case .italian: "Italiano"
        case .portuguese: "Português"
        case .greek: "Ελληνικά"
        }
    }

    /// Two letters for the language's badge, written in the language itself.
    var monogram: String {
        switch self {
        case .greek: "Ελ"
        default: rawValue.capitalized
        }
    }

    /// Voice used to read stories aloud. The Portuguese catalog is Brazilian.
    var voiceCode: String {
        switch self {
        case .french: "fr-FR"
        case .spanish: "es-ES"
        case .german: "de-DE"
        case .italian: "it-IT"
        case .portuguese: "pt-BR"
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
        case .portuguese: "Olá"
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
        case .portuguese: ["Bom dia", "Boa tarde", "Boa noite"]
        case .greek: ["Καλημέρα", "Καλό απόγευμα", "Καλησπέρα"]
        }
        return greetings[part]
    }

    /// Today's date as the language writes it: "dimanche 25 octobre".
    func longDate(_ date: Date) -> String {
        date.formatted(.dateTime.weekday(.wide).day().month(.wide).locale(Locale(identifier: voiceCode)))
    }
}
