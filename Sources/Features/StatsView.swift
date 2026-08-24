import SwiftUI
import Charts

struct StatsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var selectedDay: Date?

    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "M月d日"
        return formatter
    }()

    private var totalSeconds: Int {
        store.bookReadingSeconds.values.reduce(0, +)
    }

    private var totalChapters: Int {
        store.dailyStats.values.reduce(0) { $0 + $1.chapters }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                if totalSeconds == 0 && totalChapters == 0 {
                    emptyCard
                }
                summaryCards
                heatmapCard
                recentChartCard
                byBookCard
            }
            .padding()
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("阅读统计")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - 汇总卡片

    private var summaryCards: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
            statCard("今日阅读", StatsFormatting.duration(store.todayStat.seconds), icon: "clock")
            statCard("连续阅读", "\(store.readingStreakDays) 天", icon: "flame")
            statCard("累计阅读", StatsFormatting.duration(totalSeconds), icon: "books.vertical")
            statCard("已读章节", "\(totalChapters) 章", icon: "text.book.closed")
        }
    }

    private func statCard(_ title: String, _ value: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(title, systemImage: icon)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.title3)
                .fontWeight(.bold)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: - 热力图

    /// 最近 17 周，按周分列，最后一列结束于今天
    private var heatmapWeeks: [[Date]] {
        let calendar = Calendar(identifier: .gregorian)
        let today = calendar.startOfDay(for: Date())
        let dayCount = 17 * 7
        guard let start = calendar.date(byAdding: .day, value: -(dayCount - 1), to: today) else { return [] }
        let days = (0..<dayCount).compactMap { offset in
            calendar.date(byAdding: .day, value: offset, to: start)
        }
        return stride(from: 0, to: days.count, by: 7).map { Array(days[$0..<min($0 + 7, days.count)]) }
    }

    private var heatmapCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("阅读热力图").font(.headline)
            ScrollView(.horizontal, showsIndicators: false) {
                let weeks = heatmapWeeks
                HStack(alignment: .bottom, spacing: 4) {
                    ForEach(Array(weeks.enumerated()), id: \.offset) { weekIndex, week in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(monthLabel(weekIndex: weekIndex, weeks: weeks))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                                .frame(width: 14, alignment: .leading)
                            VStack(spacing: 4) {
                                ForEach(week, id: \.self) { day in
                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(heatColor(store.stat(for: day)))
                                        .frame(width: 14, height: 14)
                                        .onTapGesture { selectedDay = day }
                                }
                            }
                        }
                    }
                }
                .padding(.vertical, 2)
            }
            if let day = selectedDay {
                let stat = store.stat(for: day)
                Text("\(Self.dayFormatter.string(from: day)) · 阅读 \(StatsFormatting.duration(stat.seconds)) · 读完 \(stat.chapters) 章")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                Text("点按方块查看当天详情")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(14)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func monthLabel(weekIndex: Int, weeks: [[Date]]) -> String {
        let calendar = Calendar(identifier: .gregorian)
        guard weekIndex > 0, let first = weeks[weekIndex].first, let previousFirst = weeks[weekIndex - 1].first else {
            return ""
        }
        let month = calendar.component(.month, from: first)
        return month != calendar.component(.month, from: previousFirst) ? "\(month)月" : ""
    }

    private func heatColor(_ stat: DailyStat) -> Color {
        let minutes = stat.seconds / 60
        switch minutes {
        case ..<1: return Color.primary.opacity(0.08)
        case 1..<15: return Color.accentPurple.opacity(0.3)
        case 15..<40: return Color.accentPurple.opacity(0.5)
        case 40..<90: return Color.accentPurple.opacity(0.75)
        default: return Color.accentPurple
        }
    }

    // MARK: - 近两周柱状图

    private var recentChartCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("近 14 天").font(.headline)
            Chart(store.statSeries(days: 14), id: \.date) { item in
                BarMark(
                    x: .value("日期", item.date, unit: .day),
                    y: .value("分钟", Double(item.stat.seconds) / 60)
                )
                .foregroundStyle(Color.accentPurple)
                .cornerRadius(3)
            }
            .frame(height: 140)
        }
        .padding(14)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: - 按书籍排行

    private var bookEntries: [(id: String, seconds: Int)] {
        store.bookReadingSeconds
            .sorted { $0.value > $1.value }
            .map { (id: $0.key, seconds: $0.value) }
    }

    @ViewBuilder
    private var byBookCard: some View {
        if !bookEntries.isEmpty {
            VStack(alignment: .leading, spacing: 14) {
                Text("按书籍统计").font(.headline)
                let maxSeconds = max(bookEntries.map(\.seconds).max() ?? 1, 1)
                ForEach(bookEntries.prefix(8), id: \.id) { entry in
                    VStack(alignment: .leading, spacing: 5) {
                        HStack {
                            Text(store.book(withID: entry.id)?.title ?? "不在当前书源的书")
                                .font(.subheadline)
                                .lineLimit(1)
                            Spacer()
                            Text(StatsFormatting.duration(entry.seconds))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        ProgressView(value: Double(entry.seconds), total: Double(maxSeconds))
                            .tint(Color.accentPurple)
                    }
                }
            }
            .padding(14)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
    }

    private var emptyCard: some View {
        VStack(spacing: 10) {
            Image(systemName: "chart.square.dots")
                .font(.system(size: 30))
                .foregroundStyle(.tertiary)
            Text("还没有阅读记录")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("阅读时自动累计时长，读一章就会出现在这里")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }
}
