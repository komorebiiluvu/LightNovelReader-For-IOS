import Foundation

/// 自定义书架（“默认书架”即收藏 savedIDs，不在此列）
struct BookShelf: Identifiable, Codable, Hashable {
    let id: String
    var name: String
    /// 书架内书籍顺序
    var bookIDs: [String]
}
