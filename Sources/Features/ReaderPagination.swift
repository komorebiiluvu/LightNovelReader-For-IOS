#if canImport(UIKit)
import UIKit
typealias ReaderFont = UIFont
#elseif canImport(AppKit)
import AppKit
typealias ReaderFont = NSFont
#endif
import CoreText

/// 内置字体。原生字体名（真机预装）优先，开源字体（App 内置，模拟器也可用）兜底。
/// 模拟器不预装宋体/楷体/圆体，缺字体名会静默回退系统字体——所以必须运行时探测。
enum ReaderFontFamily: String, CaseIterable, Identifiable, Codable {
    case system = "系统"
    case songti = "宋体"
    case kaiti = "楷体"
    case yuanti = "圆体"

    var id: String { rawValue }

    /// 候选字体名，按优先级排列
    var candidates: [String] {
        switch self {
        case .system:
            return []
        case .songti:
            return ["Songti SC", "STSongti-SC-Regular", "NotoSerifSC-Regular", "Noto Serif SC", "NotoSerifSC"]
        case .kaiti:
            return ["Kaiti SC", "STKaiti-SC-Regular", "LXGWWenKai-Regular", "LXGW WenKai"]
        case .yuanti:
            return ["Yuanti SC", "STYuanti-SC-Regular"]
        }
    }

    /// 运行时实际可用的字体名；系统字体返回 nil
    var resolvedName: String? {
        guard self != .system else { return nil }
        return candidates.first { Self.fontResolves($0) }
    }

    /// 设置面板可选项：过滤掉当前设备不可用的字体
    static var available: [ReaderFontFamily] {
        allCases.filter { $0 == .system || $0.resolvedName != nil }
    }

    private static func fontResolves(_ name: String) -> Bool {
        let font = CTFontCreateWithName(name as CFString, 17, nil)
        if (CTFontCopyPostScriptName(font) as String).caseInsensitiveCompare(name) == .orderedSame { return true }
        if (CTFontCopyFullName(font) as String).caseInsensitiveCompare(name) == .orderedSame { return true }
        if (CTFontCopyFamilyName(font) as String).caseInsensitiveCompare(name) == .orderedSame { return true }
        return false
    }
}

/// 阅读器翻页分页：TextKit 实测文字高度装页。
/// 段落放不下时会把段首切下来填满当前页（段内断页），避免页尾大片空白。
///
/// 分页结果是纯函数：同一 (段落、容器、字号、行距、字体、字重) 永远得到相同页布局。
/// 设置面板的滑杆/步进器每动一格都会触发一次分页，主线程逐字测量成本高，
/// 所以用 NSCache 按参数签名缓存计算结果，字号/行距微调（改回上一档）时直接命中。
enum ReaderPagination {
    /// 页内段落之间的间距，需与 ReaderView 页面 VStack 的 spacing 一致
    private static let paragraphSpacing: CGFloat = 16

    /// 分页结果缓存。键 = 参数签名；值 = 页布局。
    /// 容量按"最近几次分页"够用即可：切换章节、拖动滑杆时都能命中。
    private static let pageCache: NSCache<NSString, NSArray> = {
        let cache = NSCache<NSString, NSArray>()
        cache.countLimit = 24
        return cache
    }()

    /// 生成分页缓存键：所有影响布局的参数都进键（含段落内容哈希，保证内容变了键就变）
    private static func cacheKey(
        paragraphs: [String],
        width: CGFloat,
        height: CGFloat,
        fontSize: CGFloat,
        lineSpacing: CGFloat,
        family: ReaderFontFamily,
        bold: Bool
    ) -> String {
        var hasher = Hasher()
        for paragraph in paragraphs {
            hasher.combine(paragraph)
        }
        let contentHash = hasher.finalize()
        return "\(contentHash)#\(Int(width))x\(Int(height))#\(Int(fontSize))#\(Int(lineSpacing))#\(family.rawValue)#\(bold)"
    }

    static func resolveFont(family: ReaderFontFamily, bold: Bool, size: CGFloat) -> ReaderFont {
        let base: ReaderFont
        if let name = family.resolvedName, let named = ReaderFont(name: name, size: size) {
            base = named
        } else {
            base = ReaderFont.systemFont(ofSize: size)
        }
        #if canImport(UIKit)
        if bold {
            // 优先用字体的原生 bold 变体；没有则回退系统粗体（保证加粗在视觉和分页上都生效）
            if let descriptor = base.fontDescriptor.withSymbolicTraits(.traitBold) {
                return ReaderFont(descriptor: descriptor, size: size)
            }
            return ReaderFont.systemFont(ofSize: size, weight: .bold)
        }
        #else
        if bold {
            return NSFontManager.shared.convert(base, toHaveTrait: .boldFontMask)
        }
        #endif
        return base
    }

    private static func lineHeight(of font: ReaderFont, spacing: CGFloat) -> CGFloat {
        #if canImport(UIKit)
        return font.lineHeight + spacing
        #else
        return font.boundingRectForFont.height + spacing
        #endif
    }

    static func paginate(
        paragraphs: [String],
        width: CGFloat,
        height: CGFloat,
        fontSize: CGFloat,
        lineSpacing: CGFloat,
        family: ReaderFontFamily = .system,
        bold: Bool = false
    ) -> [[String]] {
        guard !paragraphs.isEmpty, width > 0, height > 0 else { return [] }

        // 缓存命中直接返回，避免重复测量
        let key = cacheKey(
            paragraphs: paragraphs, width: width, height: height,
            fontSize: fontSize, lineSpacing: lineSpacing, family: family, bold: bold
        )
        if let cached = pageCache.object(forKey: key as NSString) as? [[String]] {
            return cached
        }

        let result = computePages(
            paragraphs: paragraphs, width: width, height: height,
            fontSize: fontSize, lineSpacing: lineSpacing, family: family, bold: bold
        )
        pageCache.setObject(result as NSArray, forKey: key as NSString)
        return result
    }

    private static func computePages(
        paragraphs: [String],
        width: CGFloat,
        height: CGFloat,
        fontSize: CGFloat,
        lineSpacing: CGFloat,
        family: ReaderFontFamily,
        bold: Bool
    ) -> [[String]] {
        let font = resolveFont(family: family, bold: bold, size: fontSize)
        let style = NSMutableParagraphStyle()
        style.lineSpacing = lineSpacing
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .paragraphStyle: style]

        func measuredHeight(_ text: String) -> CGFloat {
            NSAttributedString(string: text, attributes: attributes)
                .boundingRect(
                    with: CGSize(width: width, height: .greatestFiniteMagnitude),
                    options: [.usesLineFragmentOrigin],
                    context: nil
                )
                .height
        }

        let lineHeight = Self.lineHeight(of: font, spacing: lineSpacing)
        let charsPerLine = max(Int(width / fontSize), 6)

        // 预处理：过滤空段；单段超过一整页的按估算字符数切块
        var pieces: [String] = []
        for paragraph in paragraphs {
            let plain = paragraph.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !plain.isEmpty else { continue }
            if measuredHeight(plain) <= height {
                pieces.append(plain)
            } else {
                let chunk = charsPerLine * max(Int(height / lineHeight) - 1, 1)
                var start = plain.startIndex
                while start < plain.endIndex {
                    let end = plain.index(start, offsetBy: chunk, limitedBy: plain.endIndex) ?? plain.endIndex
                    pieces.append(String(plain[start..<end]))
                    start = end
                }
            }
        }
        guard !pieces.isEmpty else { return [] }

        var pages: [[String]] = []
        var current: [String] = []
        var used: CGFloat = 0
        var index = 0

        while index < pieces.count {
            let piece = pieces[index]
            let pieceHeight = measuredHeight(piece)
            let extra = current.isEmpty ? 0 : paragraphSpacing
            let remaining = height - used - extra

            // 整段放得下 → 直接入页
            if pieceHeight <= remaining {
                current.append(piece)
                used += pieceHeight + paragraphSpacing
                index += 1
                continue
            }

            // 剩余空间 ≥ 2 行：把段首切下来填满本页（段内断页）
            if remaining >= lineHeight * 2 {
                let linesFit = Int(remaining / lineHeight)
                var count = min(piece.count, charsPerLine * linesFit)
                while count > 0 && measuredHeight(String(piece.prefix(count))) > remaining {
                    count -= max(charsPerLine / 2, 1)
                }
                if count > 0 {
                    current.append(String(piece.prefix(count)))
                    pages.append(current)
                    current = []
                    used = 0
                    let tail = String(piece.dropFirst(count))
                    if measuredHeight(tail) > height {
                        let chunk = charsPerLine * max(Int(height / lineHeight) - 1, 1)
                        var start = tail.startIndex
                        var tailPieces: [String] = []
                        while start < tail.endIndex {
                            let end = tail.index(start, offsetBy: chunk, limitedBy: tail.endIndex) ?? tail.endIndex
                            tailPieces.append(String(tail[start..<end]))
                            start = end
                        }
                        pieces.replaceSubrange(index...index, with: tailPieces)
                    } else {
                        pieces[index] = tail
                    }
                    continue
                }
            }

            // 剩余空间太小：整段顺延到下一页
            pages.append(current)
            current = []
            used = 0
        }
        if !current.isEmpty { pages.append(current) }
        return pages
    }
}
