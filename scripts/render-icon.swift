// Renders the app icon variants into the asset catalog:
//   swift scripts/render-icon.swift
// μυ, the start of μῦθος ("story"), as Paradigma's icon is πα, the start of παράδειγμα.

import AppKit

let size = 1024
let out = URL(fileURLWithPath: "Mythos/Assets.xcassets/AppIcon.appiconset")

func color(_ hex: UInt32, _ alpha: CGFloat = 1) -> NSColor {
    NSColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
}

enum Variant { case light, dark, tinted }

func render(_ variant: Variant, to name: String) throws {
    // No alpha channel for the main icon: the App Store rejects icons with one.
    let info = variant == .light ? CGImageAlphaInfo.noneSkipLast.rawValue : CGImageAlphaInfo.premultipliedLast.rawValue
    let cg = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                       space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: info)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: cg, flipped: false)
    let rect = NSRect(x: 0, y: 0, width: size, height: size)

    switch variant {
    case .light:
        NSGradient(colors: [color(0x5B8CFF), color(0x2E5BD8), color(0x1B3A9C)])!.draw(in: rect, angle: -70)
        // A soft highlight in the upper left, like light on glass.
        NSGradient(colors: [color(0xFFFFFF, 0.28), color(0xFFFFFF, 0)])!
            .draw(fromCenter: NSPoint(x: 260, y: 820), radius: 0, toCenter: NSPoint(x: 260, y: 820), radius: 760, options: [])
    case .dark:
        NSGradient(colors: [color(0x1B2240), color(0x0B1020)])!.draw(in: rect, angle: -70)
    case .tinted:
        NSColor.black.setFill()
        rect.fill()
    }

    let font = NSFont(name: "Georgia-Bold", size: 560) ?? NSFont.boldSystemFont(ofSize: 560)
    let glyphColor: NSColor = switch variant {
    case .light: .white
    case .dark: color(0x7EA2FF)
    case .tinted: .white
    }
    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = .center
    let text = NSAttributedString(string: "μυ", attributes: [
        .font: font, .foregroundColor: glyphColor, .paragraphStyle: paragraph, .kern: -20,
    ])
    let bounds = text.boundingRect(with: rect.size, options: [.usesLineFragmentOrigin, .usesFontLeading])
    // Greek lowercase sits on the baseline with a descender (μ); nudge up a little to look centred.
    text.draw(in: NSRect(x: 0, y: (CGFloat(size) - bounds.height) / 2 + 40, width: CGFloat(size), height: bounds.height))

    NSGraphicsContext.restoreGraphicsState()
    let rep = NSBitmapImageRep(cgImage: cg.makeImage()!)
    try rep.representation(using: .png, properties: [:])!.write(to: out.appendingPathComponent(name))
}

try render(.light, to: "icon-1024.png")
try render(.dark, to: "icon-1024-dark.png")
try render(.tinted, to: "icon-1024-tinted.png")
print("Wrote icons to \(out.path)")
