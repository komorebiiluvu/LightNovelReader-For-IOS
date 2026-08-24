import SwiftUI
import UniformTypeIdentifiers

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    @AppStorage("accentTheme") private var accentTheme = AccentTheme.purple.rawValue

    @State private var showClearHistoryDialog = false
    @State private var showResetProgressDialog = false
    @State private var showClearCacheDialog = false
    @State private var cacheSize: Int64 = -1

    // 备份与恢复
    private struct BackupShareItem: Identifiable {
        let id = UUID()
        let url: URL
    }
    @State private var backupShareItem: BackupShareItem?
    @State private var showImporter = false
    @State private var backupError: String?
    @State private var importDone = false

    // 崩溃报告导出
    @State private var crashShareItem: BackupShareItem?
    @State private var showCrashClearDialog = false

    private var cacheSizeText: String {
        cacheSize < 0 ? "计算中…" : ByteCountFormatter.string(fromByteCount: cacheSize, countStyle: .file)
    }

    /// 导出崩溃报告为文本文件（分享面板）
    private func exportCrashReport() {
        let content = CrashReporter.shared.exportContent()
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("LightNovelReader-崩溃报告.txt")
        try? content.write(to: url, atomically: true, encoding: .utf8)
        crashShareItem = BackupShareItem(url: url)
    }

    /// 从 Info.plist 读取版本号
    private static var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }

    var body: some View {
        List {
            Section("外观") {
                ForEach(ThemePreference.allCases) { theme in
                    Button {
                        store.theme = theme
                    } label: {
                        HStack {
                            Text("主题 · \(theme.label)")
                                .foregroundStyle(.primary)
                            Spacer()
                            if store.theme == theme {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Color.accentPurple)
                            }
                        }
                    }
                }
            }

            Section("强调色") {
                ForEach(AccentTheme.allCases) { accent in
                    Button {
                        accentTheme = accent.rawValue
                    } label: {
                        HStack {
                            Circle()
                                .fill(Color(hex: accent.lightHex))
                                .frame(width: 18, height: 18)
                            Text(accent.rawValue)
                                .foregroundStyle(.primary)
                            Spacer()
                            if accentTheme == accent.rawValue {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Color.accentPurple)
                            }
                        }
                    }
                }
            }

            Section {
                Picker("数据源", selection: Binding(
                    get: { store.source },
                    set: { name in
                        Task { await store.setGlobalSource(name) }
                    }
                )) {
                    ForEach(store.sourceNames, id: \.self) { source in
                        Text(source)
                    }
                }
            } header: {
                Text("数据源")
            } footer: {
                Text("「文库8(在线)」为真实书源，登录后可全站搜索。")
            }

            Section("阅读数据") {
                NavigationLink {
                    StatsView()
                } label: {
                    LabeledContent("阅读统计", value: StatsFormatting.duration(store.todayStat.seconds))
                }
                Button {
                    showClearHistoryDialog = true
                } label: {
                    LabeledContent("搜索历史", value: "\(store.searchHistory.count) 条")
                        .foregroundStyle(.primary)
                }
                Button(role: .destructive) {
                    showResetProgressDialog = true
                } label: {
                    LabeledContent("阅读进度", value: "全部重置")
                        .foregroundStyle(.primary)
                }
            }

            Section {
                Button {
                    do {
                        backupShareItem = BackupShareItem(url: try store.exportBackup())
                    } catch {
                        backupError = error.localizedDescription
                    }
                } label: {
                    LabeledContent("导出备份", value: "JSON 文件")
                        .foregroundStyle(.primary)
                }
                Button {
                    showImporter = true
                } label: {
                    LabeledContent("导入备份", value: "覆盖当前数据")
                        .foregroundStyle(.primary)
                }
            } header: {
                Text("备份与恢复")
            } footer: {
                Text("备份含书架、进度、设置、统计与登录态；不含离线缓存。")
            }

            Section {
                LabeledContent("离线内容", value: cacheSizeText)
                Button(role: .destructive) {
                    showClearCacheDialog = true
                } label: {
                    Text("清除离线缓存")
                        .foregroundStyle(.primary)
                }
            } header: {
                Text("离线与存储")
            } footer: {
                Text("详情页「缓存全书」后可离线阅读。")
            }

            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Label("特别鸣谢", systemImage: "heart.fill")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.accentPurple)
                    Text("本项目深受开源项目 LightNovelReader 的启发与指引，没有它就没有这个项目。感谢作者 dmzz-yyhyy 与全体贡献者。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if let url = URL(string: "https://github.com/dmzz-yyhyy/LightNovelReader") {
                        Link(destination: url) {
                            HStack(spacing: 4) {
                                Text("LightNovelReader（Android 上游项目）")
                                Image(systemName: "arrow.up.right")
                                    .font(.caption2)
                            }
                            .font(.caption)
                        }
                    }
                }
                .padding(.vertical, 4)
            }

            Section {
                LabeledContent("版本", value: Self.appVersion)
                LabeledContent("本地数据", value: "已自动保存")
                if CrashReporter.shared.hasCrashes {
                    Button {
                        exportCrashReport()
                    } label: {
                        LabeledContent("崩溃报告", value: "\(CrashReporter.shared.crashFiles.count) 条，点按导出")
                            .foregroundStyle(.primary)
                    }
                    Button(role: .destructive) {
                        showCrashClearDialog = true
                    } label: {
                        Text("清除崩溃报告")
                            .foregroundStyle(.primary)
                    }
                }
                if let url = URL(string: "https://github.com/dmzz-yyhyy/LightNovelReader") {
                    Link(destination: url) {
                        HStack {
                            Text("GitHub 仓库")
                            Spacer()
                            Image(systemName: "arrow.up.right")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                NavigationLink {
                    LicensesView()
                } label: {
                    Text("开源许可")
                }
            } header: {
                Text("关于")
            } footer: {
                Text("本应用为界面原型，书籍内容版权归原作者及站点所有。")
            }
        }
        .navigationTitle("设置")
        .task {
            cacheSize = await store.diskUsage()
        }
        .sheet(item: $backupShareItem) { item in
            ShareSheet(items: [item.url])
        }
        .sheet(item: $crashShareItem) { item in
            ShareSheet(items: [item.url])
        }
        .confirmationDialog("清除全部崩溃报告？", isPresented: $showCrashClearDialog, titleVisibility: .visible) {
            Button("清除", role: .destructive) {
                CrashReporter.shared.clear()
            }
            Button("取消", role: .cancel) {}
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json]) { result in
            switch result {
            case .success(let url):
                let secured = url.startAccessingSecurityScopedResource()
                Task {
                    defer {
                        if secured { url.stopAccessingSecurityScopedResource() }
                    }
                    do {
                        let data = try Data(contentsOf: url)
                        try await store.importBackup(data)
                        importDone = true
                    } catch {
                        backupError = error.localizedDescription
                    }
                }
            case .failure(let error):
                backupError = error.localizedDescription
            }
        }
        .alert("导入完成", isPresented: $importDone) {
            Button("好", role: .cancel) {}
        } message: {
            Text("备份数据已恢复。")
        }
        .alert("备份失败", isPresented: Binding(
            get: { backupError != nil },
            set: { if !$0 { backupError = nil } }
        )) {
            Button("好", role: .cancel) {}
        } message: {
            Text(backupError ?? "")
        }
        .confirmationDialog("清除全部离线缓存？", isPresented: $showClearCacheDialog, titleVisibility: .visible) {
            Button("清除 \(cacheSizeText)", role: .destructive) {
                Task {
                    await store.clearDiskCache()
                    cacheSize = await store.diskUsage()
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("已缓存的章节正文与目录将被删除，离线阅读需重新缓存。")
        }
        .confirmationDialog("清除全部搜索历史？", isPresented: $showClearHistoryDialog, titleVisibility: .visible) {
            Button("清除 \(store.searchHistory.count) 条记录", role: .destructive) {
                store.clearSearchHistory()
            }
            Button("取消", role: .cancel) {}
        }
        .confirmationDialog("重置全部阅读进度？", isPresented: $showResetProgressDialog, titleVisibility: .visible) {
            Button("重置", role: .destructive) {
                store.resetReadingProgress()
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("所有书将恢复到示例数据的初始进度，已加入书架的收藏不受影响。")
        }
    }
}
