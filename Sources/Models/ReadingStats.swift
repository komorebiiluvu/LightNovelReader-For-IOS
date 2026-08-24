import Foundation

/// 单日阅读统计
struct DailyStat: Codable, Equatable {
    var seconds = 0
    var chapters = 0
}

enum StatsFormatting {
    static func duration(_ seconds: Int) -> String {
        let minutes = seconds / 60
        if minutes < 1 { return "不足 1 分钟" }
        if minutes < 60 { return "\(minutes) 分钟" }
        let hourPart = minutes / 60
        let minutePart = minutes % 60
        return minutePart > 0 ? "\(hourPart) 小时 \(minutePart) 分" : "\(hourPart) 小时"
    }
}
