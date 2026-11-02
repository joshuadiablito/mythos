import SwiftUI

// Liquid Glass on iOS 26; on iOS 18, the frosted material and bordered
// buttons it replaced. The app works the same on both, only flatter on 18.

extension View {
    /// Glass in a shape, optionally tinted: a capsule label, a round badge.
    /// `interactive` glass responds to touch (iOS 26 only).
    @ViewBuilder
    func glass(tint: Color? = nil, interactive: Bool = false, in shape: some Shape) -> some View {
        if #available(iOS 26, *) {
            glassEffect(Self.glass(tint: tint, interactive: interactive), in: shape)
        } else {
            background {
                ZStack {
                    shape.fill(.ultraThinMaterial)
                    if let tint { shape.fill(tint) }
                }
            }
        }
    }

    @available(iOS 26, *)
    private static func glass(tint: Color?, interactive: Bool) -> Glass {
        var glass = Glass.regular
        if let tint { glass = glass.tint(tint) }
        if interactive { glass = glass.interactive() }
        return glass
    }

    /// A glass button, or a prominent one tinted with the accent.
    @ViewBuilder
    func glassButtonStyle(prominent: Bool = false, shape: ButtonBorderShape = .capsule) -> some View {
        if #available(iOS 26, *) {
            if prominent { buttonStyle(.glassProminent).buttonBorderShape(shape) } else { buttonStyle(.glass).buttonBorderShape(shape) }
        } else {
            if prominent { buttonStyle(.borderedProminent).buttonBorderShape(shape) } else { buttonStyle(.bordered).buttonBorderShape(shape) }
        }
    }

    /// Toolbar buttons are glass on iOS 26 and stand out over a cover or text
    /// on their own. Before, plain buttons need their bar's material behind them.
    @ViewBuilder
    func barBackgroundsBeforeGlass() -> some View {
        if #available(iOS 26, *) {
            self
        } else {
            toolbarBackground(.visible, for: .navigationBar, .bottomBar)
        }
    }

    /// Shrinks the tab bar while scrolling down (iOS 26 only).
    @ViewBuilder
    func minimizesTabBarOnScroll() -> some View {
        if #available(iOS 26, *) {
            tabBarMinimizeBehavior(.onScrollDown)
        } else {
            self
        }
    }
}

/// Lets nearby glass shapes blend into each other on iOS 26; on iOS 18, just its content.
struct GlassGroup<Content: View>: View {
    var spacing: CGFloat?
    @ViewBuilder var content: Content

    var body: some View {
        if #available(iOS 26, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            content
        }
    }
}
