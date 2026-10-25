import SwiftUI

/// A language's monogram in a glass circle: "Fr", "Ελ".
struct LanguageBadge: View {
    let language: Language
    var size: CGFloat = 34

    var body: some View {
        Text(language.monogram)
            .font(.system(size: size * 0.4, weight: .semibold, design: .serif))
            .foregroundStyle(Theme.accent)
            .frame(width: size, height: size)
            .glassEffect(.regular.tint(Theme.accent.opacity(0.12)), in: .circle)
            .accessibilityLabel(language.name)
    }
}

/// "A1", in a small capsule.
struct LevelPill: View {
    let level: Level
    var prominent = false

    var body: some View {
        Text(level.rawValue)
            .font(.caption.weight(.bold).monospaced())
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .foregroundStyle(prominent ? .white : Theme.accent)
            .background(prominent ? AnyShapeStyle(Theme.accent) : AnyShapeStyle(Theme.accent.opacity(0.12)), in: .capsule)
            .accessibilityLabel("Level \(level.rawValue)")
    }
}

/// A row of small metadata: topic, minutes, new words.
struct StoryMeta: View {
    let story: Story

    var body: some View {
        HStack(spacing: 14) {
            Label(story.topic, systemImage: story.symbol)
            Label("\(story.readingMinutes) min", systemImage: "clock")
            Label("\(story.vocab.count) words", systemImage: "text.book.closed")
        }
        .font(.caption)
        .foregroundStyle(.secondary)
        .labelStyle(.compact)
    }
}

struct CompactLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 4) {
            configuration.icon.imageScale(.small)
            configuration.title
        }
    }
}

extension LabelStyle where Self == CompactLabelStyle {
    static var compact: CompactLabelStyle { .init() }
}

/// Lays children out left to right, wrapping onto new lines: for word chips.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.last.map { $0.y + $0.height } ?? 0
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: bounds.minY + row.y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
        }
    }

    private struct Row {
        var indices: [Int] = []
        var y: CGFloat = 0
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var current = Row()
        for (i, subview) in subviews.enumerated() {
            let size = subview.sizeThatFits(.unspecified)
            if !current.indices.isEmpty && current.width + spacing + size.width > width {
                rows.append(current)
                current = Row(y: current.y + current.height + spacing)
            }
            current.width += (current.indices.isEmpty ? 0 : spacing) + size.width
            current.height = max(current.height, size.height)
            current.indices.append(i)
        }
        if !current.indices.isEmpty { rows.append(current) }
        return rows
    }
}
