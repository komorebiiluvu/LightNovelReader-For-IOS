import SwiftUI
import UIKit

/// Apple Books 风格的单面卷页契约。
///
/// `.pageCurl` + `.min` 书脊在单面模式下，静止、程序化切页与手势切页始终只显示
/// 一个正文控制器。纸张背面由 UIKit 根据正面快照生成，因此会保留很淡的文字透印，
/// 不再把独立纯黑纸背送进系统光照管线，也不会出现 1/2 个控制器数量互相冲突。
enum PageCurlConfiguration {
    static let isDoubleSided = false
    static let targetControllerCount = 1
}

/// 单面模式只在正文页索引之间移动，不再插入人为的 back surface。
enum PageCurlPageSequence {
    static func previousIndex(from index: Int, pageCount: Int) -> Int? {
        guard pageCount > 0, index > 0, index < pageCount else { return nil }
        return index - 1
    }

    static func nextIndex(from index: Int, pageCount: Int) -> Int? {
        guard pageCount > 0, index >= 0, index < pageCount - 1 else { return nil }
        return index + 1
    }
}

/// 用 UIPageViewController(.pageCurl) 实现拟真翻页。
///
/// 关键约束：
/// 1. 始终使用单面模式和一个目标控制器，点按与滑动遵循同一页面模型；
/// 2. 点击切页延后到手势派发结束后，避免在 UITapGestureRecognizer 回调栈内重入；
/// 3. isAnimating 锁阻止一次操作触发多次切页；
/// 4. 页面控制器小范围复用，渲染签名变化时统一失效。
struct PageCurlReaderView: UIViewControllerRepresentable {
    typealias PageBuilder = (Int) -> AnyView

    let pageCount: Int
    let initialPage: Int
    let background: UIColor
    let renderToken: String
    let pageBuilder: PageBuilder
    let onEdge: (Int) -> Void
    let onPageChanged: (Int) -> Void
    let onToggleBars: () -> Void

    static func makePageViewController() -> UIPageViewController {
        let pvc = UIPageViewController(
            transitionStyle: .pageCurl,
            navigationOrientation: .horizontal,
            options: [.spineLocation: UIPageViewController.SpineLocation.min.rawValue]
        )
        pvc.isDoubleSided = PageCurlConfiguration.isDoubleSided
        return pvc
    }

    func makeUIViewController(context: Context) -> UIPageViewController {
        let pvc = Self.makePageViewController()
        pvc.dataSource = context.coordinator
        pvc.delegate = context.coordinator
        pvc.view.backgroundColor = background
        pvc.view.isOpaque = true

        context.coordinator.parent = self
        context.coordinator.pvc = pvc
        context.coordinator.lastRenderToken = renderToken

        let tap = UITapGestureRecognizer(
            target: context.coordinator,
            action: #selector(Coordinator.handleTap(_:))
        )
        tap.delegate = context.coordinator
        pvc.view.addGestureRecognizer(tap)
        for pan in pvc.gestureRecognizers.compactMap({ $0 as? UIPanGestureRecognizer }) {
            pan.addTarget(context.coordinator, action: #selector(Coordinator.handleEdgePan(_:)))
        }

        let startIndex = max(0, min(initialPage, pageCount - 1))
        let startControllers = context.coordinator.visibleControllers(at: startIndex)
        if startControllers.count == PageCurlConfiguration.targetControllerCount {
            pvc.setViewControllers(startControllers, direction: .forward, animated: false)
        }
        return pvc
    }

    func updateUIViewController(_ pvc: UIPageViewController, context: Context) {
        context.coordinator.parent = self
        // 防止 SwiftUI 更新过程中旧状态重新写回双面模式。
        pvc.isDoubleSided = PageCurlConfiguration.isDoubleSided
        pvc.view.backgroundColor = background
        if context.coordinator.lastRenderToken != renderToken {
            context.coordinator.invalidatePageCache()
            context.coordinator.lastRenderToken = renderToken
            let currentIndex = context.coordinator.currentPage(in: pvc) ?? initialPage
            let target = min(max(currentIndex, 0), max(pageCount - 1, 0))
            let controllers = context.coordinator.visibleControllers(at: target)
            if controllers.count == PageCurlConfiguration.targetControllerCount {
                pvc.setViewControllers(controllers, direction: .forward, animated: false)
            }
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    final class Coordinator: NSObject, UIPageViewControllerDataSource, UIPageViewControllerDelegate, UIGestureRecognizerDelegate {
        var parent: PageCurlReaderView
        var lastRenderToken: String?
        weak var pvc: UIPageViewController?

        private var isAnimating = false
        private var animationSequence = 0
        private var pageVCCache: [Int: PageHostController] = [:]
        private var pageVCAccessOrder: [Int] = []
        private var edgePanStartPage: Int?
        /// 单面模式只需保留当前页及其前后少量正文页。
        private let maxPageVCCache = 5

        init(_ parent: PageCurlReaderView) {
            self.parent = parent
        }

        private func purgeCacheIfRenderChanged() {
            if lastRenderToken != parent.renderToken {
                invalidatePageCache()
                lastRenderToken = parent.renderToken
            }
        }

        func invalidatePageCache() {
            pageVCCache.removeAll()
            pageVCAccessOrder.removeAll()
        }

        private func touchPage(_ index: Int) {
            pageVCAccessOrder.removeAll { $0 == index }
            pageVCAccessOrder.append(index)
        }

        func currentPage(in pvc: UIPageViewController) -> Int? {
            pvc.viewControllers?
                .compactMap { ($0 as? PageHostController)?.pageIndex }
                .first
        }

        /// `.min` + 单面 page-curl 在所有静止和程序化切页路径都只接收一个控制器。
        func visibleControllers(at index: Int) -> [UIViewController] {
            guard let page = pageVC(for: index) else { return [] }
            return [page]
        }

        // MARK: - 程序化点击翻页

        private func setPage(
            _ index: Int,
            direction: UIPageViewController.NavigationDirection
        ) {
            guard index >= 0,
                  index < parent.pageCount,
                  pvc != nil,
                  !isAnimating else { return }
            isAnimating = true
            animationSequence &+= 1
            let sequence = animationSequence

            // 先退出点击手势回调栈，再启动系统卷页；左右点击使用完全相同的路径。
            DispatchQueue.main.async { [weak self] in
                guard let self,
                      let pvc = self.pvc,
                      self.animationSequence == sequence else { return }
                let controllers = self.visibleControllers(at: index)
                guard controllers.count == PageCurlConfiguration.targetControllerCount else {
                    self.isAnimating = false
                    return
                }

                CATransaction.begin()
                CATransaction.setAnimationDuration(0.1)
                pvc.setViewControllers(
                    controllers,
                    direction: direction,
                    animated: true
                ) { [weak self, weak pvc] _ in
                    guard let self, let pvc else { return }
                    self.completePageChange(
                        requestedIndex: index,
                        in: pvc,
                        sequence: sequence
                    )
                }
                CATransaction.commit()
            }

            // UIKit 极端中断时 completion 可能不回调；超时后解除输入锁。
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { [weak self] in
                guard let self, self.animationSequence == sequence else { return }
                self.isAnimating = false
            }
        }

        private func completePageChange(
            requestedIndex: Int,
            in pvc: UIPageViewController,
            sequence: Int
        ) {
            guard animationSequence == sequence else { return }
            isAnimating = false
            parent.onPageChanged(currentPage(in: pvc) ?? requestedIndex)
        }

        /// 切章会让 SwiftUI 销毁当前 page controller，必须延后到手势回调结束之后。
        private func requestEdge(_ direction: Int) {
            guard !isAnimating else { return }
            isAnimating = true
            animationSequence &+= 1
            let sequence = animationSequence
            DispatchQueue.main.async { [weak self] in
                guard let self, self.animationSequence == sequence else { return }
                self.parent.onEdge(direction)
                self.isAnimating = false
            }
        }

        // MARK: - 点击三分区

        @objc func handleTap(_ tap: UITapGestureRecognizer) {
            guard let pvc, let currentIndex = currentPage(in: pvc) else { return }
            let x = tap.location(in: pvc.view).x
            let width = pvc.view.bounds.width
            if x < width / 3 {
                if let previous = PageCurlPageSequence.previousIndex(
                    from: currentIndex,
                    pageCount: parent.pageCount
                ) {
                    setPage(previous, direction: .reverse)
                } else {
                    requestEdge(-1)
                }
            } else if x > width * 2 / 3 {
                if let next = PageCurlPageSequence.nextIndex(
                    from: currentIndex,
                    pageCount: parent.pageCount
                ) {
                    setPage(next, direction: .forward)
                } else {
                    requestEdge(1)
                }
            } else {
                parent.onToggleBars()
            }
        }

        /// 数据源查询必须无副作用；只有用户真的在首尾页向外拖动结束后才切换章节。
        @objc func handleEdgePan(_ pan: UIPanGestureRecognizer) {
            guard let pvc else { return }
            switch pan.state {
            case .began:
                edgePanStartPage = currentPage(in: pvc)
            case .ended:
                defer { edgePanStartPage = nil }
                guard let start = edgePanStartPage,
                      currentPage(in: pvc) == start else { return }
                let translation = pan.translation(in: pvc.view).x
                let threshold = max(36, pvc.view.bounds.width * 0.12)
                if start == 0, translation > threshold {
                    requestEdge(-1)
                } else if start == parent.pageCount - 1, translation < -threshold {
                    requestEdge(1)
                }
            case .cancelled, .failed:
                edgePanStartPage = nil
            default:
                break
            }
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool {
            false
        }

        /// 让点击手势在拖拽开始时失效，防止点击和拖拽连续触发两次翻页。
        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            if gestureRecognizer is UITapGestureRecognizer,
               let pan = pvc?.gestureRecognizers.compactMap({ $0 as? UIPanGestureRecognizer }).first,
               pan.state == .began || pan.state == .changed {
                return false
            }
            return true
        }

        // MARK: - 页面构造与缓存

        func pageVC(for index: Int) -> PageHostController? {
            guard index >= 0, index < parent.pageCount else { return nil }
            purgeCacheIfRenderChanged()
            if let cached = pageVCCache[index] {
                touchPage(index)
                return cached
            }

            let host = PageHostController()
            host.pageIndex = index
            host.pageBackground = parent.background
            let contentVC = UIHostingController(rootView: parent.pageBuilder(index))
            contentVC.view.backgroundColor = parent.background
            contentVC.view.isOpaque = true
            host.content = contentVC
            host.view.accessibilityElementsHidden = false
            pageVCCache[index] = host
            touchPage(index)

            if pageVCCache.count > maxPageVCCache,
               let oldest = pageVCAccessOrder.first {
                pageVCAccessOrder.removeFirst()
                pageVCCache[oldest] = nil
            }
            return host
        }

        // MARK: - DataSource

        func pageViewController(
            _ pvc: UIPageViewController,
            viewControllerBefore vc: UIViewController
        ) -> UIViewController? {
            guard let current = vc as? PageHostController,
                  let previous = PageCurlPageSequence.previousIndex(
                    from: current.pageIndex,
                    pageCount: parent.pageCount
                  ) else { return nil }
            return pageVC(for: previous)
        }

        func pageViewController(
            _ pvc: UIPageViewController,
            viewControllerAfter vc: UIViewController
        ) -> UIViewController? {
            guard let current = vc as? PageHostController,
                  let next = PageCurlPageSequence.nextIndex(
                    from: current.pageIndex,
                    pageCount: parent.pageCount
                  ) else { return nil }
            return pageVC(for: next)
        }

        // MARK: - Delegate

        func pageViewController(
            _ pvc: UIPageViewController,
            didFinishAnimating finished: Bool,
            previousViewControllers: [UIViewController],
            transitionCompleted completed: Bool
        ) {
            isAnimating = false
            guard completed, let current = currentPage(in: pvc) else { return }
            parent.onPageChanged(current)
        }
    }
}

/// 单个正文页的 UIKit 宿主。纸张背面不再由应用创建。
final class PageHostController: UIViewController {
    var pageIndex = 0
    var pageBackground: UIColor? {
        didSet {
            loadViewIfNeeded()
            applyPaperAppearance()
        }
    }
    var content: UIViewController? {
        didSet {
            guard oldValue !== content else { return }
            oldValue?.willMove(toParent: nil)
            oldValue?.view.removeFromSuperview()
            oldValue?.removeFromParent()
            loadViewIfNeeded()
            installContentIfNeeded()
        }
    }

    override func loadView() {
        let paperView = UIView(frame: .zero)
        paperView.isOpaque = true
        view = paperView
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        applyPaperAppearance()
        installContentIfNeeded()
    }

    private func applyPaperAppearance() {
        view.backgroundColor = pageBackground
        view.isOpaque = true
        view.layer.shadowColor = nil
        view.layer.shadowOpacity = 0
        view.layer.shadowRadius = 0
        view.layer.shadowOffset = .zero
        view.layer.borderWidth = 0
        view.layer.shouldRasterize = false
        view.layer.compositingFilter = nil
        content?.view.backgroundColor = pageBackground
        content?.view.isOpaque = true
    }

    private func installContentIfNeeded() {
        guard let content, content.parent !== self else { return }
        addChild(content)
        content.view.frame = view.bounds
        content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        content.view.backgroundColor = pageBackground
        content.view.isOpaque = true
        view.addSubview(content.view)
        content.didMove(toParent: self)
    }
}
