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
}
