import SwiftUI

/// 封面渐变占位配色（真实封面加载失败时兜底）
let coverPalette: [[Color]] = [
    [Color(hex: 0x5A4FCF), Color(hex: 0x8F6BE0)],
    [Color(hex: 0x2E7D6B), Color(hex: 0x67C09A)],
    [Color(hex: 0xB4557F), Color(hex: 0xE2859F)],
    [Color(hex: 0x3B5BDB), Color(hex: 0x6D8BF0)],
    [Color(hex: 0xC17B26), Color(hex: 0xE5B15F)],
    [Color(hex: 0x4A7A9B), Color(hex: 0x7FB3D5)],
    [Color(hex: 0x7A4FB0), Color(hex: 0xB28AE0)],
    [Color(hex: 0x9C5A4F), Color(hex: 0xD58F7F)],
    [Color(hex: 0x35686E), Color(hex: 0x6BA6AD)]
]

/// 章节标题占位词（目录未加载时兜底，如「第 3 章 面包」）
let chapterNames = ["转生", "小镇", "卫兵", "面包", "委托", "同行", "城门", "酒馆", "地图", "夜宿"]
