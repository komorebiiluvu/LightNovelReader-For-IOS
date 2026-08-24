import SwiftUI

@main
struct LightNovelReaderApp: App {
    @StateObject private var store = AppStore()

    init() {
        // 崩溃捕获：崩溃时记录到沙盒，设置页可导出（云 Mac 无法直连 iPad，靠此功能取崩溃日志）
        CrashReporter.shared.install()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
        }
    }
}
