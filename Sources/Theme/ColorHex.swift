import SwiftUI
import UIKit

extension UIColor {
    convenience init(hex: UInt, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xff) / 255,
            green: CGFloat((hex >> 8) & 0xff) / 255,
            blue: CGFloat(hex & 0xff) / 255,
            alpha: alpha
        )
    }
}

/// 用户可选的强调色（主题色）
enum AccentTheme: String, CaseIterable, Identifiable {
    case purple = "浅紫"
    case blue = "蓝"
    case teal = "青绿"
    case pink = "粉"
    case orange = "橙"
    case red = "红"

    var id: String { rawValue }

    var lightHex: UInt {
        switch self {
        case .purple: return 0x6750A4
        case .blue: return 0x2563EB
        case .teal: return 0x0D9488
        case .pink: return 0xDB2777
        case .orange: return 0xEA580C
        case .red: return 0xDC2626
        }
    }

    var darkHex: UInt {
        switch self {
        case .purple: return 0xD0BCFF
        case .blue: return 0x93C5FD
        case .teal: return 0x5EEAD4
        case .pink: return 0xF9A8D4
        case .orange: return 0xFDBA74
        case .red: return 0xFCA5A5
        }
    }

    /// 当前用户选择的主题色（从 UserDefaults 读取）
    static var current: AccentTheme {
        let raw = UserDefaults.standard.string(forKey: "accentTheme") ?? purple.rawValue
        return AccentTheme(rawValue: raw) ?? .purple
    }

    /// 当前主题色（按系统明暗返回对应色值）
    var color: Color {
        Color(uiColor: UIColor { trait in
            trait.userInterfaceStyle == .dark ? UIColor(hex: self.darkHex) : UIColor(hex: self.lightHex)
        })
    }
}

extension Color {
    init(hex: UInt, alpha: Double = 1) {
        self.init(uiColor: UIColor(hex: hex, alpha: CGFloat(alpha)))
    }

    /// 当前用户选择的强调色（从 UserDefaults 读取，跨页面一致）
    static var accentPurple: Color {
        AccentTheme.current.color
    }
}
