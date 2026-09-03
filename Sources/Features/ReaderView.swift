import SwiftUI
import UIKit

enum ReaderMode: String, CaseIterable, Identifiable, Codable {
    case flip = "仿真翻页"
    case scroll = "滚动"
    var id: String { rawValue }
}

/// 滚动阅读的跨章手势判定。必须从本章底部开始一次新的上滑，避免普通的
/// 惯性滚动刚好抵达章末时直接跳走。
enum ReaderScrollChapterAdvancePolicy {
    static let upwardPullThreshold: CGFloat = 48
    /// 原生滚动值可能受 safe area、缩放和小数像素影响；在距底部 24pt 内开始
    /// 新手势均视为用户已经读到章末。
    static let bottomTolerance: CGFloat = 24

    static func isAtBottom(contentOffsetY: CGFloat, maximumOffsetY: CGFloat) -> Bool {
        contentOffsetY >= maximumOffsetY - bottomTolerance
    }

    static func shouldAdvance(
        startedAtBottom: Bool,
        verticalTranslation: CGFloat,
        canAdvance: Bool
    ) -> Bool {
        startedAtBottom
            && canAdvance
            && verticalTranslation <= -upwardPullThreshold
    }
}

/// 直接观察 SwiftUI ScrollView 背后的 UIScrollView 原生拖动手势。
///
/// iOS 16 下额外叠加的 SwiftUI DragGesture 可能被滚动容器取消，尤其是短章节
/// 没有可滚动距离时。这里不给手势添加竞争识别器，只作为现有 panGestureRecognizer
/// 的 target 读取真实 contentOffset 和 translation，因此不会改变正常滚动物理。
private struct ReaderScrollPullMonitor: UIViewRepresentable {
    let canAdvance: Bool
    let onAdvance: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(canAdvance: canAdvance, onAdvance: onAdvance)
    }

    func makeUIView(context: Context) -> ObserverView {
        let view = ObserverView()
        view.isUserInteractionEnabled = false
        view.coordinator = context.coordinator
        return view
    }

    func updateUIView(_ uiView: ObserverView, context: Context) {
        context.coordinator.canAdvance = canAdvance
        context.coordinator.onAdvance = onAdvance
        uiView.coordinator = context.coordinator
        uiView.attachIfPossible()
    }

    static func dismantleUIView(_ uiView: ObserverView, coordinator: Coordinator) {
        coordinator.detach()
    }

    final class ObserverView: UIView {
        weak var coordinator: Coordinator?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            attachIfPossible()
            // SwiftUI 有时会在 didMoveToWindow 后一拍才把承载视图放进 UIScrollView。
            DispatchQueue.main.async { [weak self] in
                self?.attachIfPossible()
            }
        }

        func attachIfPossible() {
            coordinator?.attach(from: self)
        }
    }

    final class Coordinator: NSObject {
        var canAdvance: Bool
        var onAdvance: () -> Void

        private weak var scrollView: UIScrollView?
        private weak var panGesture: UIPanGestureRecognizer?
        private var previousAlwaysBounceVertical: Bool?
        private var startedAtBottom = false
        private var didTrigger = false

        init(canAdvance: Bool, onAdvance: @escaping () -> Void) {
            self.canAdvance = canAdvance
            self.onAdvance = onAdvance
        }

        func attach(from observer: UIView) {
            var ancestor = observer.superview
            while let view = ancestor, !(view is UIScrollView) {
                ancestor = view.superview
            }
            guard let found = ancestor as? UIScrollView else { return }
            guard scrollView !== found else { return }

            detach()
            scrollView = found
            let pan = found.panGestureRecognizer
            pan.addTarget(self, action: #selector(handlePan(_:)))
            panGesture = pan
            // 不足一屏的短章节也必须允许在章末继续上拉。
            previousAlwaysBounceVertical = found.alwaysBounceVertical
            found.alwaysBounceVertical = true
        }

        func detach() {
            panGesture?.removeTarget(self, action: #selector(handlePan(_:)))
            if let previousAlwaysBounceVertical {
                scrollView?.alwaysBounceVertical = previousAlwaysBounceVertical
            }
            previousAlwaysBounceVertical = nil
            panGesture = nil
            scrollView = nil
            startedAtBottom = false
            didTrigger = false
        }

        @objc private func handlePan(_ pan: UIPanGestureRecognizer) {
            guard let scrollView else { return }
            switch pan.state {
            case .began:
                let minimumOffsetY = -scrollView.adjustedContentInset.top
                let maximumOffsetY = max(
                    minimumOffsetY,
                    scrollView.contentSize.height
                        - scrollView.bounds.height
                        + scrollView.adjustedContentInset.bottom
                )
                startedAtBottom = ReaderScrollChapterAdvancePolicy.isAtBottom(
                    contentOffsetY: scrollView.contentOffset.y,
                    maximumOffsetY: maximumOffsetY
                )
                didTrigger = false

            case .changed:
                guard !didTrigger,
                      ReaderScrollChapterAdvancePolicy.shouldAdvance(
                          startedAtBottom: startedAtBottom,
                          verticalTranslation: pan.translation(in: scrollView).y,
                          canAdvance: canAdvance
                      ) else { return }
                didTrigger = true
                // 下一拍再切章，避免在 UIScrollView 正分发 pan 回调时销毁承载视图。
                DispatchQueue.main.async { [weak self] in
                    guard let self, self.canAdvance else { return }
                    self.onAdvance()
                }

            case .ended, .cancelled, .failed:
                startedAtBottom = false
                didTrigger = false

            default:
                break
            }
        }
    }
}

/// 滚动模式章内位置上报
private struct ReaderScrollFractionKey: PreferenceKey {
    static var defaultValue: Double = 0
    static func reduce(value: inout Double, nextValue: () -> Double) {
        value = nextValue()
    }
}

/// 滚动模式正文面板：把逐帧的滚动位置变化隔离在子视图内，
/// 只在跨过 2% 步进时才通知父视图，避免整个阅读器每帧重绘。
private struct ReaderScrollSurface: View {
    let content: ChapterContent
    let chapterNumber: Int
    let chapterLabelText: String
    let bodyFont: Font
    let lineSpacing: CGFloat
    let captionColor: Color
    let prefs: ReaderPreferences
    let restoreFraction: Double
    let canAdvanceChapter: Bool
    let onToggleBars: () -> Void
    let onFractionChange: (Double) -> Void
    let onNextChapter: () -> Void
    /// 子视图销毁（切章/退出阅读）时上报最终位置；章号由子视图自带，避免父视图状态时序错位
    let onPersist: (Int, Double) -> Void

    @State private var reportedFraction: Double = 0
    @State private var isRequestingNextChapter = false

    var body: some View {
        GeometryReader { viewport in
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text(chapterLabelText)
                            .font(.caption)
                            .foregroundStyle(captionColor)
                        Text("第 \(chapterNumber + 1) 章")
                            .font(.title2)
                            .fontWeight(.bold)
                        LazyVStack(alignment: .leading, spacing: 16) {
                            ForEach(Array(content.paragraphs.enumerated()), id: \.offset) { offset, paragraph in
                                Text(paragraph)
                                    .font(bodyFont)
                                    .lineSpacing(lineSpacing)
                                    .id("para-\(offset)")
                            }
                            ForEach(content.images, id: \.self) { urlString in
                                scrollImage(urlString)
                            }
                        }
                        Text(canAdvanceChapter ? "继续上滑进入下一章" : "已到最后一章")
                            .font(.caption)
                            .foregroundStyle(captionColor)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 12)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, prefs.marginLeft)
                    .padding(.trailing, prefs.marginRight)
                    .padding(.top, prefs.marginTop)
                    // 上下使用同一用户边距；额外 16pt 会在隐藏 UI 后形成明显的底部空带。
                    .padding(.bottom, prefs.marginBottom)
                    .background(
                        ZStack {
                            GeometryReader { geo in
                                Color.clear.preference(
                                    key: ReaderScrollFractionKey.self,
                                    value: min(max(
                                        Double(-geo.frame(in: .named("readerScroll")).minY)
                                            / max(Double(geo.size.height - viewport.size.height), 1),
                                        0), 1)
                                )
                            }
                            ReaderScrollPullMonitor(
                                canAdvance: canAdvanceChapter && !isRequestingNextChapter,
                                onAdvance: requestNextChapter
                            )
                            .frame(width: 0, height: 0)
                        }
                    )
                }
                .coordinateSpace(name: "readerScroll")
                // 沉浸阅读：仅点击屏幕中间 1/3 唤起/收起顶栏底栏（与翻页模式三分区一致）
                .gesture(
                    SpatialTapGesture(coordinateSpace: .local)
                        .onEnded { value in
                            let w = value.location.x
                            if w >= UIScreen.main.bounds.width / 3,
                               w <= UIScreen.main.bounds.width * 2 / 3 {
                                onToggleBars()
                            }
                        }
                )
                .onPreferenceChange(ReaderScrollFractionKey.self) { value in
                    guard abs(value - reportedFraction) > 0.02 || value >= 1 else { return }
                    reportedFraction = value
                    onFractionChange(value)
                }
                .task(id: chapterNumber) {
                    restorePosition(proxy)
                }
            }
        }
        .onDisappear {
            onPersist(chapterNumber, reportedFraction)
        }
    }

    private func requestNextChapter() {
        guard !isRequestingNextChapter, canAdvanceChapter else { return }
        isRequestingNextChapter = true
        onNextChapter()
    }

    private func restorePosition(_ proxy: ScrollViewProxy) {
        guard content.paragraphs.count > 0, restoreFraction > 0.02 else { return }
        let fraction = min(restoreFraction, 1)
        let paragraphs = content.paragraphs
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
            if fraction > 0.97 {
                proxy.scrollTo("para-\(paragraphs.count - 1)", anchor: .bottom)
            } else {
                let target = min(Int(Double(paragraphs.count) * fraction), paragraphs.count - 1)
                proxy.scrollTo("para-\(target)", anchor: .top)
            }
        }
    }

    @ViewBuilder
    private func scrollImage(_ urlString: String) -> some View {
        if let url = URL(string: urlString) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFit()
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                case .failure:
                    Text("插图加载失败")
                        .font(.caption2)
                        .foregroundStyle(captionColor)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 30)
                default:
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 50)
                }
            }
        }
    }
}

struct ReaderBackground {
    let name: String
    let background: Color
    let foreground: Color
    /// 深色背景（决定 UI 控件用白色还是黑色）
    let isDark: Bool
}

let readerBackgrounds: [ReaderBackground] = [
    ReaderBackground(name: "米白", background: Color(hex: 0xFDF8EE), foreground: Color(hex: 0x1B1B1F), isDark: false),
    ReaderBackground(name: "羊皮", background: Color(hex: 0xF6EFE2), foreground: Color(hex: 0x3A332B), isDark: false),
    ReaderBackground(name: "深灰", background: Color(hex: 0x1C1C24), foreground: Color(hex: 0xCFD0DC), isDark: true),
    ReaderBackground(name: "夜间", background: Color(hex: 0x000000), foreground: Color(hex: 0xE6E1E9), isDark: true),
    ReaderBackground(name: "护眼", background: Color(hex: 0xDCEBD8), foreground: Color(hex: 0x1F2B1E), isDark: false)
]

/// 阅读背景只由阅读器自己的选择和 iOS 系统外观决定，不读取 App 的外观偏好。
enum ReaderBackgroundPolicy {
    static let followSystemIndex = readerBackgrounds.count
    static let systemLightIndex = 0
    static let systemDarkIndex = 3

    static func resolvedIndex(selection: Int, systemIsDark: Bool) -> Int {
        if selection == followSystemIndex {
            return systemIsDark ? systemDarkIndex : systemLightIndex
        }
        return min(max(selection, 0), readerBackgrounds.count - 1)
    }
}

/// 从 UIScreen 读取真实的 iOS 外观；它不受当前 App 窗口 preferredColorScheme 覆盖影响。
enum SystemAppearance {
    static var isDark: Bool {
        UIScreen.main.traitCollection.userInterfaceStyle == .dark
    }
}

/// 系统外观变化探针。回调读取 window.screen，而不是被 App 外观覆盖后的 view.traitCollection。
private struct SystemAppearanceProbe: UIViewRepresentable {
    let onChange: (Bool) -> Void

    func makeUIView(context: Context) -> ProbeView {
        let view = ProbeView()
        view.onChange = onChange
        view.publishCurrentAppearance()
        return view
    }

    func updateUIView(_ uiView: ProbeView, context: Context) {
        uiView.onChange = onChange
        uiView.publishCurrentAppearance()
    }

    final class ProbeView: UIView {
        var onChange: ((Bool) -> Void)?
        private var lastValue: Bool?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            publishCurrentAppearance()
        }

        override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
            super.traitCollectionDidChange(previousTraitCollection)
            publishCurrentAppearance()
        }

        func publishCurrentAppearance() {
            let style = window?.screen.traitCollection.userInterfaceStyle
                ?? UIScreen.main.traitCollection.userInterfaceStyle
            let dark = style == .dark
            guard dark != lastValue else { return }
            lastValue = dark
            DispatchQueue.main.async { [weak self] in
                self?.onChange?(dark)
            }
        }
    }
}

struct ReaderView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    /// 「跟随系统」在背景索引中的位置（readerBackgrounds 之外的第 6 项）
    static let followSystemBackgroundIndex = ReaderBackgroundPolicy.followSystemIndex

    let book: Book
    let startChapter: Int

    @State private var chapter: Int
    @State private var pageIndex = 0
    /// 跨章连翻到上一章末页时的待处理标记
    @State private var pendingAtEnd = false
    @State private var showSettings = false
    @State private var showCatalog = false
    @State private var showBars = false
    @State private var now = Date()
    @State private var pillExpanded = false
    @State private var batteryLevel: Float = UIDevice.current.batteryLevel
    @State private var isCharging = false
    @State private var systemIsDark = SystemAppearance.isDark

    // 阅读时长统计：readingStart 非 nil 表示计时中
    @State private var readingStart: Date?
    @State private var lastStatChapter: Int

    // 滚动模式的章内位置（0~1）
    @State private var scrollFraction: Double = 0

    // 正文按章异步加载
    @State private var content: ChapterContent?
    @State private var contentError: String?

    private let minuteTimer = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    init(book: Book, startChapter: Int) {
        self.book = book
        self.startChapter = startChapter
        _chapter = State(initialValue: startChapter)
        _lastStatChapter = State(initialValue: startChapter)
    }

    /// 以 store 里的最新版本为准（目录回填 totalChapters、换源都会即时生效）
    private var liveBook: Book {
        store.book(withID: book.id) ?? book
    }

    private var paragraphs: [String] {
        content?.paragraphs ?? []
    }

    // 按实测文字高度分页的结果（翻页模式）
    @State private var laidPages: [[String]] = []
    /// 分页版本号：每次重新分页后递增，用于触发翻页视图重建（设置即时生效）
    @State private var paginationVersion = 0

    /// 翻页视图的渲染签名：分页版本 + 全部阅读设置 + 强调色 + 已解析的阅读背景。
    /// 任一变化都会让 PageCurlReaderView 重建当前页，实现设置即时响应（无需翻页）
    private var renderToken: String {
        let accent = AccentTheme.resolve(
            UserDefaults.standard.string(forKey: AccentTheme.storageKey)
        ).rawValue
        let p = store.readerPreferences
        return "\(paginationVersion)|\(resolvedBackgroundIndex)|\(p.fontFamily.rawValue)|\(Int(p.fontSize))|\(Int(p.lineSpacing))|\(p.bold)|\(Int(p.marginLeft))|\(Int(p.marginRight))|\(Int(p.marginTop))|\(Int(p.marginBottom))|\(p.mode.rawValue)|\(accent)"
    }

    private func layoutKey(_ size: CGSize) -> String {
        "\(chapter)#\(content?.paragraphs.count ?? 0)#\(Int(fontSize))#\(Int(lineSpacing))#\(prefs.fontFamily.rawValue)#\(prefs.bold)#\(Int(prefs.marginLeft))#\(Int(prefs.marginRight))#\(Int(prefs.marginTop))#\(Int(prefs.marginBottom))#\(Int(size.width))x\(Int(size.height))"
    }

    /// 后台分页任务：布局参数或章节内容变化时取消旧的、只保留最新一次
    @State private var paginationTask: Task<Void, Never>?

    private func computePages(size: CGSize) {
        paginationTask?.cancel()
        // 可用区域 = 屏幕 - 四边自定义边距 - 顶栏缓冲(16) - 页首章节标题行(31)
        let width = max(size.width - prefs.marginLeft - prefs.marginRight, 100)
        let height = max(size.height - prefs.marginTop - prefs.marginBottom - 47, 100)
        let paragraphs = self.paragraphs
        let fontSize = self.fontSize
        let lineSpacing = self.lineSpacing
        let family = prefs.fontFamily
        let bold = prefs.bold

        paginationTask = Task.detached(priority: .userInitiated) {
            // 分页测量在主线程外执行（NSString boundingRect 对读操作线程安全），
            // 避免设置面板拖动滑杆时整页卡顿；期间沿用旧分页继续显示
            let result = ReaderPagination.paginate(
                paragraphs: paragraphs,
                width: width,
                height: height,
                fontSize: fontSize,
                lineSpacing: lineSpacing,
                family: family,
                bold: bold,
                shouldCancel: { Task.isCancelled }
            )
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard !Task.isCancelled else { return }
                applyPagination(result, size: size)
            }
        }
    }

    @MainActor
    private func applyPagination(_ result: [[String]], size: CGSize) {
        // 记录重排前的阅读进度比例（字号/边距等变化导致页数变化时，按比例校准位置）
        let oldTotal = totalPages
        let oldPage = pageIndex
        let progressRatio = oldTotal > 0 ? Double(oldPage) / Double(oldTotal) : 0

        laidPages = result

        // 跨章连翻到上一章末页时，跳到最后一页
        if pendingAtEnd {
            pageIndex = max(totalPages - 1, 0)
            pendingAtEnd = false
        } else if oldTotal > 0 && totalPages != oldTotal {
            // 页数变化（字号等重排）：按阅读进度比例校准，保持当前阅读位置不丢失
            pageIndex = min(Int(progressRatio * Double(totalPages)), max(totalPages - 1, 0))
        } else {
            pageIndex = min(pageIndex, max(totalPages - 1, 0))
        }

        // 递增分页版本，触发翻页视图重建
        paginationVersion += 1
    }

    private var resolvedBackgroundIndex: Int {
        ReaderBackgroundPolicy.resolvedIndex(
            selection: store.readerPreferences.backgroundIndex,
            systemIsDark: systemIsDark
        )
    }

    private var bg: ReaderBackground { readerBackgrounds[resolvedBackgroundIndex] }

    /// 阅读器自身控制状态栏、设置 Sheet 等系统 UI 的明暗，不继承设置页的 App 外观。
    private var readerColorScheme: ColorScheme { bg.isDark ? .dark : .light }
    /// UI 控件（底栏、返回键、药丸）单色：深色背景用白，浅色背景用黑
    private var uiColor: Color { bg.isDark ? .white : .black }
    private var prefs: ReaderPreferences { store.readerPreferences }
    private var fontSize: CGFloat { store.readerPreferences.fontSize }
    private var lineSpacing: CGFloat { store.readerPreferences.lineSpacing }
    private var mode: ReaderMode { store.readerPreferences.mode }
    private var bodyFont: Font {
        let weight: Font.Weight = prefs.bold ? .bold : .regular
        if prefs.bold, prefs.fontFamily != .system {
            // 非系统字体加粗：若无原生粗体变体，SwiftUI 的 .weight 对 custom 字体可能不生效，
            // 与分页逻辑一致地回退到系统粗体，保证加粗真正可见。
            let name = prefs.fontFamily.resolvedName
            if let name, let uiFont = UIFont(name: name, size: fontSize),
               uiFont.fontDescriptor.withSymbolicTraits(.traitBold) != nil {
                return Font.custom(name, size: fontSize).weight(.bold)
            }
            return .system(size: fontSize, weight: .bold)
        }
        if let name = prefs.fontFamily.resolvedName {
            return Font.custom(name, size: fontSize)
        }
        return .system(size: fontSize, weight: weight)
    }
    private var chapterLabel: String {
        content?.title ?? Book.chapterTitle(chapter)
    }
    /// 章内已读比例（翻页按页、滚动按位置；目录页统计用）
    private var chapterReadProgress: Double? {
        if mode == .flip {
            return totalPages > 0 ? Double(pageIndex + 1) / Double(totalPages) : nil
        }
        return scrollFraction > 0 ? scrollFraction : nil
    }
    private var imagePageCount: Int {
        content?.images.count ?? 0
    }
    /// 翻页模式总页数 = 文字页 + 插图页
    private var totalPages: Int {
        laidPages.count + imagePageCount
    }

    var body: some View {
        ZStack {
            bg.background.ignoresSafeArea()
            contentArea
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                .ignoresSafeArea(.container, edges: .vertical)
            SystemAppearanceProbe { dark in
                if systemIsDark != dark {
                    systemIsDark = dark
                }
            }
            .frame(width: 0, height: 0)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
            // 药丸展开时，点击内容区任意空白处收起药丸
            if pillExpanded {
                Color.black.opacity(0.001)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        withAnimation(.spring(response: 0.38, dampingFraction: 0.82)) {
                            pillExpanded = false
                        }
                    }
                    .allowsHitTesting(true)
            }
        }
        .foregroundStyle(bg.foreground)
        .overlay(alignment: .top) {
            if showBars {
                header
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .overlay(alignment: .bottom) {
            if showBars {
                VStack(spacing: 10) {
                    if pillExpanded {
                        ReaderBookPanel(
                            book: liveBook,
                            chapter: chapter,
                            foreground: uiColor,
                            background: bg.background
                        )
                        .transition(.scale(scale: 0.85, anchor: .bottom).combined(with: .opacity))
                    }
                    ReaderStatusBar(
                        book: liveBook,
                        chapterLabel: chapterLabel,
                        foreground: uiColor,
                        background: bg.background,
                        now: now,
                        batteryLevel: batteryLevel,
                        isCharging: isCharging,
                        pillExpanded: $pillExpanded,
                        onOpenSettings: { showSettings = true },
                        onOpenCatalog: { showCatalog = true }
                    )
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .onAppear {
            UIApplication.shared.isIdleTimerDisabled = true
            UIDevice.current.isBatteryMonitoringEnabled = true
            batteryLevel = UIDevice.current.batteryLevel
            isCharging = UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full
            store.registerReading(book)  // 确保未加书架的书也能进最近阅读
            store.recordReading(bookID: book.id, chapter: chapter)
            readingStart = Date()
        }
        .onDisappear {
            UIApplication.shared.isIdleTimerDisabled = false
            UIDevice.current.isBatteryMonitoringEnabled = false
            store.recordReading(bookID: book.id, chapter: chapter)
            flushReadingTime()
            // 同步落盘，避免异步 persist 尚未写入时 App 被杀导致进度丢失
            store.flushPersist()
        }
        .onChange(of: showBars) { shown in
            if !shown { pillExpanded = false }
        }
        .onChange(of: chapter) { newChapter in
            pageIndex = 0
            scrollFraction = 0
            store.recordReading(bookID: book.id, chapter: newChapter)
            if newChapter == lastStatChapter + 1 {
                store.recordChapterRead(bookID: book.id)
            }
            lastStatChapter = newChapter
            store.flushPersist()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                systemIsDark = SystemAppearance.isDark
                if readingStart == nil { readingStart = Date() }
            } else {
                flushReadingTime()
            }
        }
        .onReceive(minuteTimer) { date in
            now = date
            // App 即使强制浅色/深色，也定期读取 UIScreen 的真实系统外观（含日落自动切换）。
            systemIsDark = SystemAppearance.isDark
            tickReadingClock()
        }
        .onReceive(NotificationCenter.default.publisher(for: UIDevice.batteryLevelDidChangeNotification)) { _ in
            batteryLevel = UIDevice.current.batteryLevel
            isCharging = UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full
        }
        .onReceive(NotificationCenter.default.publisher(for: UIDevice.batteryStateDidChangeNotification)) { _ in
            isCharging = UIDevice.current.batteryState == .charging || UIDevice.current.batteryState == .full
        }
        .task(id: chapter) {
            await store.loadChapters(for: liveBook)
            await loadContent()
        }
        .sheet(isPresented: $showSettings) {
            ReaderSettingsSheet(
                preferences: $store.readerPreferences,
                systemIsDark: systemIsDark
            )
        }
        .sheet(isPresented: $showCatalog) {
            CatalogView(book: liveBook, currentChapter: chapter, chapterProgress: chapterReadProgress) { index in
                goToChapter(index)
            }
        }
        // 沉浸阅读：顶栏/底栏隐藏时同步隐藏系统状态栏
        .statusBarHidden(!showBars)
        .preferredColorScheme(readerColorScheme)
    }

    private func loadContent() async {
        content = nil
        contentError = nil
        do {
            content = try await store.chapterContent(for: liveBook, index: chapter)
        } catch {
            contentError = error.localizedDescription
        }
    }

    // MARK: - 翻页与切章

    private func pageBackward() {
        if pageIndex > 0 {
            withAnimation { pageIndex -= 1 }
        } else if chapter > 0 {
            goToChapter(chapter - 1)
        }
    }

    private func pageForward() {
        if pageIndex < totalPages - 1 {
            withAnimation { pageIndex += 1 }
        } else if liveBook.totalChapters == 0 || chapter < liveBook.totalChapters - 1 {
            goToChapter(chapter + 1)
        }
    }

    private func goToChapter(_ index: Int, atEnd: Bool = false) {
        guard index >= 0 else { return }
        let clamped = AppStore.clampChapter(index, total: liveBook.totalChapters)
        // 先记录目标章，正文加载后再设 pageIndex（末页需等 laidPages 算好）
        pendingAtEnd = atEnd
        chapter = clamped
        pageIndex = 0
    }

    // MARK: - 阅读时长统计

    /// 每分钟定时器触发：把已累积的时长落账并重新起表
    private func tickReadingClock() {
        if readingStart != nil {
            flushReadingTime()
        }
        if scenePhase == .active {
            readingStart = Date()
        }
    }

    private func flushReadingTime() {
        guard let start = readingStart else { return }
        readingStart = nil
        let seconds = Int(Date().timeIntervalSince(start))
        if seconds > 0 {
            store.recordReadingTime(bookID: book.id, seconds: seconds)
        }
    }

    // MARK: - 上下栏

    /// 顶栏只保留左上角返回键；目录与设置在底部状态栏右侧
    private var header: some View {
        HStack {
            Button {
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.title3.weight(.medium))
                    .foregroundStyle(uiColor)
                    .frame(width: 38, height: 38)
                    .background(Circle().fill(uiColor.opacity(0.10)))
            }
            .padding(.leading, 10)
            Spacer()
        }
        .padding(.vertical, 4)
    }

    // MARK: - 内容区

    @ViewBuilder
    private var contentArea: some View {
        if let contentError {
            VStack(spacing: 14) {
                Image(systemName: "wifi.exclamationmark")
                    .font(.system(size: 34))
                    .foregroundStyle(bg.foreground.opacity(0.5))
                Text(contentError)
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                Button {
                    Task { await loadContent() }
                } label: {
                    Text("重试")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .padding(.horizontal, 22)
                        .padding(.vertical, 9)
                        .background(bg.foreground.opacity(0.12), in: Capsule())
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding()
        } else if let content {
            readerPages(content)
        } else {
            ProgressView("正在加载本章…")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    @ViewBuilder
    private func readerPages(_ content: ChapterContent) -> some View {
        // 纯插图章节：翻页模式 = 每页一张插图；滚动模式 = 竖向图片列表
        if mode == .scroll {
            scrollPages(content)
        } else {
            flipPages
        }
    }

    // MARK: 插图

    @ViewBuilder
    private func readerImage(_ urlString: String) -> some View {
        if let url = URL(string: urlString) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFit()
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                case .failure:
                    VStack(spacing: 6) {
                        Image(systemName: "photo")
                            .foregroundStyle(bg.foreground.opacity(0.4))
                        Text("插图加载失败")
                            .font(.caption2)
                            .foregroundStyle(bg.foreground.opacity(0.5))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 40)
                default:
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 60)
                }
            }
        }
    }

    // MARK: 滚动模式（滚动位置由子视图隔离追踪，避免逐帧重绘整个阅读器）

    private func scrollPages(_ content: ChapterContent) -> some View {
        ReaderScrollSurface(
            content: content,
            chapterNumber: chapter,
            chapterLabelText: chapterLabel.middleTruncated(head: 8, tail: 6),
            bodyFont: bodyFont,
            lineSpacing: lineSpacing,
            captionColor: bg.foreground.opacity(0.6),
            prefs: prefs,
            restoreFraction: store.chapterOffset(bookID: book.id, chapter: chapter),
            canAdvanceChapter: liveBook.totalChapters == 0 || chapter < liveBook.totalChapters - 1,
            onToggleBars: {
                withAnimation(.easeInOut(duration: 0.2)) { showBars.toggle() }
            },
            onFractionChange: { scrollFraction = $0 },
            onNextChapter: {
                guard liveBook.totalChapters == 0 || chapter < liveBook.totalChapters - 1 else { return }
                goToChapter(chapter + 1, atEnd: false)
            },
            onPersist: { chapterNumber, fraction in
                store.recordChapterOffset(bookID: book.id, chapter: chapterNumber, fraction: fraction)
            }
        )
        .id(chapter)
    }

    // MARK: 翻页模式（UIPageViewController.pageCurl 苹果同款卷曲）

    private var flipPages: some View {
        GeometryReader { geo in
            Group {
                if totalPages == 0 {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    PageCurlReaderView(
                        pageCount: totalPages,
                        initialPage: pageIndex,
                        background: UIColor(bg.background),
                        renderToken: renderToken,
                        pageBuilder: { index in
                            AnyView(flipPage(at: index))
                        },
                        onEdge: { direction in
                            // 跨章：上一章（-1）跳到末页，下一章（+1）跳到首页
                            if direction < 0, chapter > 0 {
                                goToChapter(chapter - 1, atEnd: true)
                            } else if direction > 0, liveBook.totalChapters == 0 || chapter < liveBook.totalChapters - 1 {
                                goToChapter(chapter + 1, atEnd: false)
                            }
                        },
                        onPageChanged: { newIndex in
                            pageIndex = newIndex
                            store.recordReading(bookID: book.id, chapter: chapter)
                        },
                        onToggleBars: {
                            withAnimation(.easeInOut(duration: 0.2)) { showBars.toggle() }
                        }
                    )
                    .id("\(chapter)#\(renderToken)")  // 设置变化（背景/字体/分页）时整体重建，立即生效
                    .id(chapter)  // 换章时重建（重置到新章第一页）
                }
            }
            .task(id: layoutKey(geo.size)) {
                computePages(size: geo.size)
            }
        }
        // 分页区域恒为全屏：状态栏显示/隐藏不改变可用高度，避免 layoutKey 变化
        // 触发重新分页导致翻页位置重置（退回章节第一页）
        .ignoresSafeArea()
    }

    /// 第 index 页的内容（越界返回空）；整体铺满背景色，避免 UIHostingController 系统背景（暗色=黑纱）露出
    @ViewBuilder
    private func flipPage(at index: Int) -> some View {
        Group {
            if index >= 0, index < laidPages.count {
                textPage(laidPages[index], pageNumber: index + 1)
            } else if let images = content?.images, index - laidPages.count >= 0, index - laidPages.count < images.count {
                imageFlipPage(images[index - laidPages.count])
            } else {
                Color.clear
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(bg.background)
        // 每个 UIHostingController 内部也忽略自己的 safe area，避免隐藏状态栏后正文上移、底部留带。
        .ignoresSafeArea(.container, edges: .all)
    }

    @ViewBuilder
    private func imageFlipPage(_ urlString: String?) -> some View {
        GeometryReader { geo in
            VStack(spacing: 10) {
                Text(chapterLabel.middleTruncated(head: 8, tail: 6))
                    .font(.caption)
                    .foregroundStyle(bg.foreground.opacity(0.6))
                if let urlString {
                    // 图片在剩余区域内居中、等比完整显示
                    readerImage(urlString)
                        .frame(width: geo.size.width, height: max(geo.size.height - 28, 40))
                }
            }
            .padding(.horizontal, max(prefs.marginLeft, 12))
            .padding(.top, prefs.marginTop)
        }
    }

    private func textPage(_ page: [String], pageNumber: Int) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("\(chapterLabel.middleTruncated(head: 8, tail: 6)) · \(pageNumber)/\(laidPages.count)")
                .font(.caption)
                .foregroundStyle(bg.foreground.opacity(0.6))
            ForEach(page, id: \.self) { paragraph in
                Text(paragraph)
                    .font(bodyFont)
                    .lineSpacing(lineSpacing)
            }
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, prefs.marginLeft)
        .padding(.trailing, prefs.marginRight)
        .padding(.top, prefs.marginTop)
        .padding(.bottom, prefs.marginBottom + 16)
    }
}
