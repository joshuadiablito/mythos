import SwiftUI

/// Mythos's palette: one cool accent (Aegean blue) for actions, one warm
/// note (apricot) for vocabulary, on a quiet paper background that gives
/// Liquid Glass something soft to refract.
enum Theme {
    static let accent = Color(light: 0x2E5BD8, dark: 0x4F7DF5)
    static let warm = Color(light: 0xD9773A, dark: 0xF2B27E)
    static let paper = Color(light: 0xF6F4EF, dark: 0x0D1117)
    static let card = Color(light: 0xFFFFFF, dark: 0x171C25)
    static let ink = Color(light: 0x1C2333, dark: 0xE8ECF4)

    /// The story text face: a serif reads like a book.
    static func storyFont(size: CGFloat = 21) -> Font { .system(size: size, weight: .regular, design: .serif) }
}

extension Color {
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                           blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
        })
    }
}

/// The app's background: paper, with a soft accent glow at the top and a
/// warm one low down, so glass bars and buttons have colour to pick up.
struct AppBackground: View {
    var body: some View {
        ZStack {
            Theme.paper
            RadialGradient(colors: [Theme.accent.opacity(0.16), .clear], center: .init(x: 0.15, y: 0), startRadius: 0, endRadius: 520)
            RadialGradient(colors: [Theme.warm.opacity(0.10), .clear], center: .init(x: 1, y: 0.85), startRadius: 0, endRadius: 460)
        }
        .ignoresSafeArea()
    }
}

extension View {
    /// A content card: solid, softly shadowed. Glass is kept for controls, as iOS does.
    func card(padding: CGFloat = 20, radius: CGFloat = 26) -> some View {
        self
            .padding(padding)
            .background(Theme.card, in: .rect(cornerRadius: radius, style: .continuous))
            .shadow(color: .black.opacity(0.06), radius: 18, y: 8)
    }

    func appBackground() -> some View {
        background { AppBackground() }
            .scrollContentBackground(.hidden)
    }
}
