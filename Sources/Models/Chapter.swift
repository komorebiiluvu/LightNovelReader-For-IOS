import Foundation

/// 目录里的单个章节条目
struct ChapterItem: Identifiable, Hashable, Codable {
    let index: Int
    let title: String
    /// 所属分卷名（如“第一卷”），真实书源才有
    var volume: String? = nil
    /// 书源侧的章节 id（wenku8 的 cid），模拟源为 nil
    var remoteID: Int? = nil
    var id: Int { index }
}

/// 章节正文
struct ChapterContent: Hashable, Codable {
    let index: Int
    let title: String
    let paragraphs: [String]
    /// 章节内的插图地址（绝对 URL）；离线时会被替换为本地缓存文件
    var images: [String] = []

    init(index: Int, title: String, paragraphs: [String], images: [String] = []) {
        self.index = index
        self.title = title
        self.paragraphs = paragraphs
        self.images = images
    }

    enum CodingKeys: String, CodingKey {
        case index, title, paragraphs, images
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        index = try container.decode(Int.self, forKey: .index)
        title = try container.decode(String.self, forKey: .title)
        paragraphs = try container.decode([String].self, forKey: .paragraphs)
        // 旧缓存没有 images 字段
        images = try container.decodeIfPresent([String].self, forKey: .images) ?? []
    }
}
