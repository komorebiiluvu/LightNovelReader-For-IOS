import SwiftUI
import UIKit

struct RootView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showSplash = true
    @AppStorage("accentTheme") private var accentTheme = AccentTheme.blue.rawValue

    var body: some View {
        ZStack {
            // 用 UIKit UITabBarController 桥接，确保标签栏在 iPad 上也固定在底部
            // （iPadOS 18+ 的 SwiftUI TabView 默认把标签栏放到顶部，与 iPhone/iOS17 行为不一致）
            // .id(accentTheme)：主题色变化时重建整个 tab 树，使所有 Color.accentPurple 即时重求值
            LegacyTabBarController(store: store, accentTheme: accentTheme)
                .id(accentTheme)
                .ignoresSafeArea()
                .preferredColorScheme(store.theme.colorScheme)

            // 开屏遮罩：数据加载完成 + 至少 1.5s 后淡出
            if showSplash {
                SplashView()
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        // 全局强调色：所有系统控件（Button/Link/Toggle 等）默认用主题色，而非系统蓝
        .tint(AccentTheme(rawValue: accentTheme)?.color ?? .accentPurple)
        .task {
            await handleAppLaunch()
        }
    }

    /// 启动时间栅栏：并发等待「至少 1.5s」与「数据加载完成」，取两者中耗时更长的，
    /// 然后平滑淡出开屏页。保证冷启动/热启动/缓存命中都至少展示 1.5s。
    private func handleAppLaunch() async {
        async let minimumTimer: Void = Task.sleep(nanoseconds: 1_500_000_000)
        async let loadData: Void = store.bootstrap()
        _ = await (try? minimumTimer, loadData)
        withAnimation(.easeInOut(duration: 0.35)) {
            showSplash = false
        }
    }
}

/// 底部标签栏桥接：包 4 个 NavigationStack，行为与旧版底部 tab bar 一致
private struct LegacyTabBarController: UIViewControllerRepresentable {
    @MainActor let store: AppStore
    let accentTheme: String

    func makeUIViewController(context: Context) -> UITabBarController {
        let tab = UITabBarController()
        // iPadOS 18+ 默认把 UIKit 标签栏也渲染成顶部样式，强制回传统底部 tab bar
        if #available(iOS 18.0, *) {
            tab.mode = .tabBar
        }
        tab.viewControllers = [
            host(ReadingView(), title: "阅读", icon: "house.fill"),
            host(BookshelfView(), title: "书架", icon: "books.vertical.fill"),
            host(ExploreView(), title: "探索", icon: "safari.fill"),
            host(SettingsView(), title: "设置", icon: "slider.horizontal.3"),
        ]
        let appearance = UITabBarAppearance()
        appearance.configureWithDefaultBackground()
        tab.tabBar.standardAppearance = appearance
        tab.tabBar.scrollEdgeAppearance = appearance
        tab.tabBar.tintColor = UIColor(hex: (AccentTheme(rawValue: accentTheme) ?? .purple).lightHex)
        return tab
    }

    func updateUIViewController(_ uiViewController: UITabBarController, context: Context) {
        // 主题色变化时同步 tab bar tint
        uiViewController.tabBar.tintColor = UIColor(hex: (AccentTheme(rawValue: accentTheme) ?? .purple).lightHex)
    }

    private func host(_ view: some View, title: String, icon: String) -> UIViewController {
        // 直接用 UIHostingController 承载 NavigationStack，不要再包一层 UINavigationController
        // （否则会形成双层导航容器，导致 push 嵌套、返回要点多次）
        let hosting = UIHostingController(rootView:
            NavigationStack {
                view
            }
            .environmentObject(store)
        )
        hosting.tabBarItem = UITabBarItem(title: title, image: UIImage(systemName: icon), selectedImage: nil)
        return hosting
    }
}
