import Foundation
import Compression

// MARK: - 导出格式与错误

enum ExportFormat: String, CaseIterable, Identifiable {
    case epub
    case txt
    var id: String { rawValue }
    var label: String { self == .epub ? "EPUB 电子书" : "TXT 文本" }
}

enum ExportBookError: LocalizedError {
    case catalogNotReady
    var errorDescription: String? { "目录尚未加载完成，请稍后再试" }
}

// MARK: - 极简 ZIP 写入器

/// 自实现的 ZIP 写入（仅写入侧，零第三方依赖）。
/// EPUB 要求 mimetype 条目必须以“存储不压缩”方式排在首位。
private struct ZipWriter {
    private struct Entry {
        let name: Data
        let data: Data
        let crc: UInt32
        let payload: Data
        let method: UInt16
        let dosTime: UInt16
        let dosDate: UInt16
    }

    private var entries: [Entry] = []
    private let dosTime: UInt16
    private let dosDate: UInt16

    init(date: Date = Date()) {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        let c = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: date)
        dosTime = UInt16(((c.hour ?? 0) << 11) | ((c.minute ?? 0) << 5) | ((c.second ?? 0) / 2))
        dosDate = UInt16((((c.year ?? 1980) - 1980) << 9) | ((c.month ?? 1) << 5) | (c.day ?? 1))
    }

    mutating func add(name: String, data: Data, compress: Bool = true) {
        let deflated = compress ? Self.deflate(data) : nil
        let useDeflate = deflated.map { $0.count < data.count } ?? false
        entries.append(Entry(
            name: Data(name.utf8),
            data: data,
            crc: Self.crc32(data),
            payload: useDeflate ? deflated! : data,
            method: useDeflate ? 8 : 0,
            dosTime: dosTime,
            dosDate: dosDate
        ))
    }

    func write(to url: URL) throws {
        var body = Data()
        var central = Data()

        for entry in entries {
            let offset = UInt32(body.count)

            // 本地文件头
            body.appendLE(UInt32(0x04034b50))
            body.appendLE(UInt16(20))        // version needed
            body.appendLE(UInt16(0x0800))    // UTF-8 文件名
            body.appendLE(entry.method)
            body.appendLE(entry.dosTime)
            body.appendLE(entry.dosDate)
            body.appendLE(entry.crc)
            body.appendLE(UInt32(entry.payload.count))
            body.appendLE(UInt32(entry.data.count))
            body.appendLE(UInt16(entry.name.count))
            body.appendLE(UInt16(0))         // extra length
            body.append(entry.name)
            body.append(entry.payload)

            // 中央目录条目
            central.appendLE(UInt32(0x02014b50))
            central.appendLE(UInt16(20))     // version made by
            central.appendLE(UInt16(20))     // version needed
            central.appendLE(UInt16(0x0800))
            central.appendLE(entry.method)
            central.appendLE(entry.dosTime)
            central.appendLE(entry.dosDate)
            central.appendLE(entry.crc)
            central.appendLE(UInt32(entry.payload.count))
            central.appendLE(UInt32(entry.data.count))
            central.appendLE(UInt16(entry.name.count))
            central.appendLE(UInt16(0))      // extra length
            central.appendLE(UInt16(0))      // comment length
            central.appendLE(UInt16(0))      // disk number
            central.appendLE(UInt16(0))      // internal attributes
            central.appendLE(UInt32(0))      // external attributes
            central.appendLE(offset)
            central.append(entry.name)
        }

        var out = body
        out.append(central)
        let count = UInt16(truncatingIfNeeded: entries.count)

        // 目录记录结束块
        out.appendLE(UInt32(0x06054b50))
        out.appendLE(UInt16(0))
        out.appendLE(UInt16(0))
        out.appendLE(count)
        out.appendLE(count)
        out.appendLE(UInt32(central.count))
        out.appendLE(UInt32(body.count))
        out.appendLE(UInt16(0))

        try out.write(to: url, options: .atomic)
    }

    // MARK: 压缩与校验

    /// Compression 框架的 COMPRESSION_ZLIB 输出的是裸 DEFLATE 流（RFC 1951），正是 ZIP 条目需要的格式
    private static func deflate(_ data: Data) -> Data? {
        guard !data.isEmpty else { return nil }
        var destination = Data(count: data.count)
        let written = destination.withUnsafeMutableBytes { destinationBuffer -> Int in
            data.withUnsafeBytes { sourceBuffer in
                compression_encode_buffer(
                    destinationBuffer.bindMemory(to: UInt8.self).baseAddress!, data.count,
                    sourceBuffer.bindMemory(to: UInt8.self).baseAddress!, data.count,
                    nil, COMPRESSION_ZLIB
                )
            }
        }
        guard written > 0 else { return nil }
        destination.removeSubrange(written..<destination.count)
        return destination
    }

    private static let crcTable: [UInt32] = (0..<256).map { index -> UInt32 in
        var value = UInt32(index)
        for _ in 0..<8 {
            value = (value & 1) != 0 ? (0xEDB88320 ^ (value >> 1)) : (value >> 1)
        }
        return value
    }

    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        for byte in data {
            crc = crcTable[Int((crc ^ UInt32(byte)) & 0xFF)] ^ (crc >> 8)
        }
        return crc ^ 0xFFFF_FFFF
    }
}

private extension Data {
    mutating func appendLE<T: FixedWidthInteger>(_ value: T) {
        var little = value.littleEndian
        Swift.withUnsafeBytes(of: &little) { append(contentsOf: $0) }
    }
}

// MARK: - EPUB 3 生成器

/// 输入纯文本章节，输出标准 EPUB 3 文件（附带 EPUB2 兼容的 toc.ncx）
enum EpubExporter {
    static func writeEpub(
        title: String,
        author: String,
        chapters: [(title: String, paragraphs: [String])],
        coverData: Data?,
        to destination: URL
    ) throws {
        let identifier = stableIdentifier(for: title)
        let escapedTitle = xmlEscape(title)

        var zip = ZipWriter()
        zip.add(name: "mimetype", data: Data("application/epub+zip".utf8), compress: false)
        zip.add(name: "META-INF/container.xml", data: Data(containerXML.utf8))
        zip.add(name: "OEBPS/styles.css", data: Data(stylesheet.utf8))

        if let coverData {
            zip.add(name: "OEBPS/cover.jpg", data: coverData)
            zip.add(name: "OEBPS/cover.xhtml", data: Data(coverXHTML(title: escapedTitle).utf8))
        }

        var manifest = """
        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
        <item id="css" href="styles.css" media-type="text/css"/>
        """
        if coverData != nil {
            manifest += "\n<item id=\"cover-image\" href=\"cover.jpg\" media-type=\"image/jpeg\" properties=\"cover-image\"/>"
            manifest += "\n<item id=\"cover-page\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>"
        }

        var spine = coverData != nil ? "<itemref idref=\"cover-page\"/>" : ""
        var tocLinks = ""
        var navPoints = ""

        for (index, chapter) in chapters.enumerated() {
            let file = chapterFile(index)
            zip.add(name: "OEBPS/\(file)", data: Data(chapterXHTML(title: chapter.title, paragraphs: chapter.paragraphs).utf8))
            manifest += "\n<item id=\"c\(index + 1)\" href=\"\(file)\" media-type=\"application/xhtml+xml\"/>"
            spine += "\n<itemref idref=\"c\(index + 1)\"/>"
            let escaped = xmlEscape(chapter.title)
            tocLinks += "<li><a href=\"\(file)\">\(escaped)</a></li>\n"
            navPoints += "<navPoint id=\"n\(index + 1)\" playOrder=\"\(index + 1)\"><navLabel><text>\(escaped)</text></navLabel><content src=\"\(file)\"/></navPoint>\n"
        }

        zip.add(name: "OEBPS/nav.xhtml", data: Data(navXHTML(title: escapedTitle, links: tocLinks).utf8))
        zip.add(name: "OEBPS/toc.ncx", data: Data(ncx(identifier: identifier, title: escapedTitle, points: navPoints).utf8))
        zip.add(name: "OEBPS/content.opf", data: Data(opf(identifier: identifier, title: escapedTitle, author: author, hasCover: coverData != nil, manifest: manifest, spine: spine).utf8))

        try zip.write(to: destination)
    }

    // MARK: 各部件模板

    private static let containerXML = """
    <?xml version="1.0" encoding="utf-8"?>
    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
    <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
    </rootfiles>
    </container>
    """

    private static let stylesheet = """
    body { line-height: 1.9; font-family: serif; margin: 0.05em; }
    h1, h2 { text-align: center; font-weight: bold; margin: 1.2em 0 1em; }
    p { text-indent: 2em; margin: 0 0 0.65em 0; }
    """

    private static func coverXHTML(title: String) -> String {
        """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" lang="zh" xml:lang="zh">
        <head>
        <meta charset="utf-8"/>
        <title>\(title)</title>
        </head>
        <body>
        <div style="text-align: center; padding-top: 12%;">
        <img src="cover.jpg" alt="封面" style="max-width: 100%; max-height: 90%;"/>
        </div>
        </body>
        </html>
        """
    }

    private static func chapterXHTML(title: String, paragraphs: [String]) -> String {
        var body = "<h2>\(xmlEscape(title))</h2>"
        for paragraph in paragraphs where !paragraph.isEmpty {
            body += "\n<p>\(xmlEscape(paragraph))</p>"
        }
        return """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" lang="zh" xml:lang="zh">
        <head>
        <meta charset="utf-8"/>
        <title>\(xmlEscape(title))</title>
        <link rel="stylesheet" type="text/css" href="styles.css"/>
        </head>
        <body>
        \(body)
        </body>
        </html>
        """
    }

    private static func navXHTML(title: String, links: String) -> String {
        """
        <?xml version="1.0" encoding="utf-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh" xml:lang="zh">
        <head>
        <meta charset="utf-8"/>
        <title>目录</title>
        <link rel="stylesheet" type="text/css" href="styles.css"/>
        </head>
        <body>
        <nav epub:type="toc" id="toc">
        <h1>\(title)</h1>
        <ol>
        \(links)</ol>
        </nav>
        </body>
        </html>
        """
    }

    private static func ncx(identifier: String, title: String, points: String) -> String {
        """
        <?xml version="1.0" encoding="utf-8"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
        <head>
        <meta name="dtb:uid" content="\(identifier)"/>
        </head>
        <docTitle>
        <text>\(title)</text>
        </docTitle>
        <navMap>
        \(points)</navMap>
        </ncx>
        """
    }

    private static func opf(identifier: String, title: String, author: String, hasCover: Bool, manifest: String, spine: String) -> String {
        """
        <?xml version="1.0" encoding="utf-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id" xml:lang="zh">
        <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
        <dc:identifier id="pub-id">\(identifier)</dc:identifier>
        <dc:title>\(title)</dc:title>
        <dc:creator>\(xmlEscape(author))</dc:creator>
        <dc:language>zh</dc:language>
        <meta property="dcterms:modified">\(modifiedTimestamp())</meta>
        </metadata>
        <manifest>
        \(manifest)
        </manifest>
        <spine toc="ncx">
        \(spine)
        </spine>
        </package>
        """
    }

    // MARK: 工具

    private static func chapterFile(_ index: Int) -> String {
        String(format: "chap%04d.xhtml", index + 1)
    }

    /// FNV-1a 哈希生成稳定 id：同一本书多次导出标识一致
    private static func stableIdentifier(for title: String) -> String {
        var hash: UInt64 = 0xcbf29ce484222325
        for byte in title.utf8 {
            hash = (hash ^ UInt64(byte)) &* 0x100000001b3
        }
        return "urn:lnr:\(String(format: "%016llx", hash))"
    }

    private static func modifiedTimestamp() -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        let c = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second], from: Date())
        return String(format: "%04d-%02d-%02dT%02d:%02d:%02dZ", c.year!, c.month!, c.day!, c.hour!, c.minute!, c.second!)
    }

    private static func xmlEscape(_ text: String) -> String {
        text.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&apos;")
    }
}
