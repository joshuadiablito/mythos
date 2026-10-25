import SwiftUI

/// The colours of a story's cover, chosen by its topic: the sea for travel,
/// terracotta for cooking, dusk for the neighbour who plays at night.
struct CoverPalette {
    /// Deep to light, plus an accent for the highlights.
    let deep: Color, mid: Color, light: Color, accent: Color

    init(_ deep: UInt32, _ mid: UInt32, _ light: UInt32, _ accent: UInt32) {
        self.deep = Color(hex: deep)
        self.mid = Color(hex: mid)
        self.light = Color(hex: light)
        self.accent = Color(hex: accent)
    }

    static let aegean = CoverPalette(0x14307F, 0x2E5BD8, 0x8FB8FF, 0x5FD3E8)
    static let dawn = CoverPalette(0x34265F, 0xD9648C, 0xFFC9A8, 0x7AA7FF)
    static let terracotta = CoverPalette(0x7A2E1E, 0xC9583A, 0xF2B279, 0xE8C15A)
    static let olive = CoverPalette(0x2F4A26, 0x6E8F45, 0xD4DE9B, 0xE7B94C)
    static let lavender = CoverPalette(0x2F2461, 0x6C5BC4, 0xC6B8FF, 0xF08FC0)
    static let rose = CoverPalette(0x7C1E45, 0xD2477A, 0xFFB8CE, 0xFF9F6E)
    static let peach = CoverPalette(0x9A3F2C, 0xE97D5B, 0xFFD6B8, 0xF7B267)
    static let mint = CoverPalette(0x0D4F55, 0x229A94, 0xA6E8D2, 0xF4D35E)
    static let sky = CoverPalette(0x284C74, 0x5E92C6, 0xD7E7F5, 0xF2C14E)
    static let transit = CoverPalette(0x053F5C, 0x1B7F8E, 0xF5C451, 0xE4572E)
    static let dusk = CoverPalette(0x1C1A40, 0x5B3E8A, 0xF29E6D, 0xFFD08A)
    static let citrus = CoverPalette(0xA44A00, 0xF2A20D, 0xFFE89A, 0xFF6B4A)
    static let pitch = CoverPalette(0x0E4A24, 0x2E9E4F, 0xB6EFC3, 0xF7F06D)
    static let ink = CoverPalette(0x141A2E, 0x3F4C78, 0xAAB6D6, 0x7EA2FF)

    static func forTopic(_ topic: String) -> CoverPalette {
        switch topic {
        case "Family": .peach
        case "Routine", "Plans": .dawn
        case "Food & drink", "Cooking": .terracotta
        case "Shopping", "Outdoors": .olive
        case "Learning", "Culture": .lavender
        case "Celebrations", "Friends": .rose
        case "Home", "Health": .mint
        case "Weather": .sky
        case "Getting around": .transit
        case "Everyday life", "Neighbours": .dusk
        case "Animals", "Free time": .citrus
        case "Sport": .pitch
        case "Work": .ink
        case "Travel": .aegean
        default: .aegean
        }
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

/// A story's cover: a mesh gradient in its topic's colours, arranged
/// differently for every story, with its symbol drawn large across it.
/// `animated` lets the colours drift slowly, for the cover on Today.
struct StoryCover: View {
    let story: Story
    var animated = false
    /// How large the symbol is drawn, relative to the cover's width.
    var symbolScale: CGFloat = 1.05

    private var palette: CoverPalette { CoverPalette.forTopic(story.topic) }

    /// A stable number from the story's ID, so its cover never changes.
    private var seed: Int {
        story.id.unicodeScalars.reduce(5381) { ($0 &* 33 &+ Int($1.value)) & 0x7FFFFFFF }
    }

    var body: some View {
        ZStack {
            if animated {
                TimelineView(.animation(minimumInterval: 1 / 30)) { context in
                    mesh(time: context.date.timeIntervalSinceReferenceDate)
                }
            } else {
                mesh(time: 0)
            }
            GeometryReader { geo in
                Image(systemName: story.symbol)
                    .resizable()
                    .scaledToFit()
                    .fontWeight(.light)
                    .frame(width: geo.size.width * symbolScale, height: geo.size.height * symbolScale)
                    .foregroundStyle(.white.opacity(0.13))
                    .blendMode(.plusLighter)
                    .rotationEffect(.degrees(Double(seed % 21) - 10))
                    .position(x: geo.size.width * (seed % 2 == 0 ? 0.78 : 0.26), y: geo.size.height * 0.34)
            }
            // A soft vignette at the bottom, so white text on top of it stays readable.
            LinearGradient(colors: [.clear, .black.opacity(0.28)], startPoint: .center, endPoint: .bottom)
        }
        .accessibilityHidden(true)
    }

    private func mesh(time: Double) -> some View {
        let p = palette
        // Nine colours, rotated by the seed so each story's cover falls differently.
        let ring: [Color] = [p.deep, p.mid, p.light, p.accent, p.mid, p.deep, p.light, p.mid]
        let shift = seed % ring.count
        let outer = (0..<8).map { ring[($0 + shift) % ring.count] }
        let colors: [Color] = [outer[0], outer[1], outer[2],
                               outer[7], seed % 3 == 0 ? p.accent : p.mid, outer[3],
                               outer[6], outer[5], outer[4]]
        let drift = animated ? 0.08 : 0
        let s = Double(seed % 100) / 100
        func wobble(_ phase: Double) -> Float { Float(drift * sin(time * 0.35 + phase * 6.28)) }
        let points: [SIMD2<Float>] = [
            [0, 0], [0.5 + wobble(s), 0], [1, 0],
            [0, 0.5 + wobble(s + 0.3)],
            [Float(0.35 + s * 0.3) + wobble(s + 0.5), Float(0.45 + (1 - s) * 0.2) + wobble(s + 0.8)],
            [1, 0.5 + wobble(s + 0.6)],
            [0, 1], [0.5 + wobble(s + 0.9), 1], [1, 1],
        ]
        return MeshGradient(width: 3, height: 3, points: points, colors: colors, smoothsColors: true)
    }
}

#Preview {
    if let story = Catalog.shared.stories.first {
        StoryCover(story: story, animated: true)
            .frame(width: 300, height: 400)
            .clipShape(.rect(cornerRadius: 32))
    }
}
