import SwiftUI
import UIKit

/// 用 UIPageViewController(.pageCurl) 实现的仿真翻页（苹果图书同款卷曲）。
/// 关键优化：
/// 1. CATransaction.setAnimationDuration 缩短翻页动画时长（约 0.22s，缓解阴影卡顿感）；
/// 2. isAnimating 锁：动画进行中忽略新的点击/拖拽，防止动画被打断、左翻失灵；
/// 3. 点击三分区手势移入内部，与拖拽手势统一由 UIPageViewController 驱动，单轨不冲突。
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

    func makeUIViewController(context: Context) -> UIPageViewController {
        let pvc = UIPageViewController(
            transitionStyle: .pageCurl,
            navigationOrientation: .horizontal,
            options: [.spineLocation: UIPageViewController.SpineLocation.min.rawValue]
        )
        pvc.isDoubleSided = false
        pvc.dataSource = context.coordinator
        pvc.delegate = context.coordinator
        pvc.view.backgroundColor = background
        pvc.view.isOpaque = true

        context.coordinator.parent = self
        context.coordinator.pvc = pvc
        context.coordinator.lastRenderToken = renderToken

        // 点击三分区手势（与 UIPageViewController 自带 pan 手势并存）
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleTap(_:)))
        tap.delegate = context.coordinator
        pvc.view.addGestureRecognizer(tap)

        if let start = context.coordinator.pageVC(at: max(0, min(initialPage, pageCount - 1))) {
            pvc.setViewControllers([start], direction: .forward, animated: false)
        }
        return pvc
    }

    func updateUIViewController(_ pvc: UIPageViewController, context: Context) {
        context.coordinator.parent = self
        if context.coordinator.lastRenderToken != renderToken {
            context.coordinator.lastRenderToken = renderToken
            let currentIndex = (pvc.viewControllers?.first as? PageHostController)?.index ?? initialPage
            let target = min(max(currentIndex, 0), max(pageCount - 1, 0))
            if let vc = context.coordinator.pageVC(at: target) {
                pvc.setViewControllers([vc], direction: .forward, animated: false)
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
        /// 动画进行中锁：翻页动画未完成时忽略新的输入，防止中断
        private var isAnimating = false

        init(_ parent: PageCurlReaderView) {
            self.parent = parent
        }

        // MARK: - 统一翻页（CATransaction 缩短动画时长）

        /// 缩短 UIPageViewController pageCurl 动画时长的技巧：
        /// setViewControllers 的动画时长受当前 CATransaction 的 animationDuration 影响。
        private func setPage(_ vc: UIViewController?, direction: UIPageViewController.NavigationDirection) {
            guard let pvc, let vc, !isAnimating else { return }
            isAnimating = true
            CATransaction.begin()
            CATransaction.setAnimationDuration(0.1)
            pvc.setViewControllers([vc], direction: direction, animated: true) { [weak self] _ in
                self?.isAnimating = false
            }
            CATransaction.commit()
        }

        // MARK: - 点击三分区

        @objc func handleTap(_ tap: UITapGestureRecognizer) {
            guard let pvc, let current = pvc.viewControllers?.first as? PageHostController else { return }
            let x = tap.location(in: pvc.view).x
            let width = pvc.view.bounds.width
            if x < width / 3 {
                if current.index > 0 {
                    setPage(pageVC(at: current.index - 1), direction: .reverse)
                } else {
                    parent.onEdge(-1)
                }
            } else if x > width * 2 / 3 {
                if current.index < parent.pageCount - 1 {
                    setPage(pageVC(at: current.index + 1), direction: .forward)
                } else {
                    parent.onEdge(1)
                }
            } else {
                parent.onToggleBars()
            }
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            // 点击翻页与拖拽翻页不要同时识别，避免一次操作触发两次翻页
            false
        }

        /// 让点击手势在拖拽开始时失效，防止点击+拖拽连续触发两次翻页
        func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
            if gestureRecognizer is UITapGestureRecognizer,
               let pan = pvc?.gestureRecognizers.compactMap({ $0 as? UIPanGestureRecognizer }).first,
               pan.state == .began || pan.state == .changed {
                return false
            }
            return true
        }

        // MARK: - 页构造

        func pageVC(at index: Int) -> PageHostController? {
            guard index >= 0, index < parent.pageCount else { return nil }
            let host = PageHostController()
            host.index = index
            host.pageBackground = parent.background
            let contentVC = UIHostingController(rootView: parent.pageBuilder(index))
            contentVC.view.backgroundColor = parent.background
            contentVC.view.isOpaque = true
            host.content = contentVC
            host.view.backgroundColor = parent.background
            host.view.isOpaque = true
            return host
        }

        // MARK: - DataSource（拖拽翻页，同样用 CATransaction 不适用，但拖拽动画由系统控制）
        // 拖拽手势由 UIPageViewController 自己处理，其动画时长不受 CATransaction 影响，
        // 但拖拽本身是跟手的，体验可接受。

        func pageViewController(_ pvc: UIPageViewController, viewControllerBefore vc: UIViewController) -> UIViewController? {
            guard let current = vc as? PageHostController else { return nil }
            if current.index <= 0 {
                parent.onEdge(-1)
                return nil
            }
            return pageVC(at: current.index - 1)
        }

        func pageViewController(_ pvc: UIPageViewController, viewControllerAfter vc: UIViewController) -> UIViewController? {
            guard let current = vc as? PageHostController else { return nil }
            if current.index >= parent.pageCount - 1 {
                parent.onEdge(1)
                return nil
            }
            return pageVC(at: current.index + 1)
        }

        // MARK: - Delegate

        func pageViewController(_ pvc: UIPageViewController, didFinishAnimating finished: Bool, previousViewControllers: [UIViewController], transitionCompleted completed: Bool) {
            isAnimating = false
            guard completed, let current = pvc.viewControllers?.first as? PageHostController else { return }
            parent.onPageChanged(current.index)
        }
    }
}

/// 一页的宿主控制器（带页码）
final class PageHostController: UIViewController {
    var index: Int = 0
    var pageBackground: UIColor? {
        didSet {
            view.backgroundColor = pageBackground
            view.isOpaque = true
            if let content {
                content.view.backgroundColor = pageBackground
                content.view.isOpaque = true
            }
        }
    }
    var content: UIViewController? {
        didSet {
            oldValue?.view.removeFromSuperview()
            oldValue?.removeFromParent()
            if let content {
                addChild(content)
                content.view.frame = view.bounds
                content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
                content.view.backgroundColor = pageBackground
                content.view.isOpaque = true
                view.addSubview(content.view)
                content.didMove(toParent: self)
            }
        }
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = pageBackground
        view.isOpaque = true
        if let content {
            addChild(content)
            content.view.frame = view.bounds
            content.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            content.view.backgroundColor = pageBackground
            content.view.isOpaque = true
            view.addSubview(content.view)
            content.didMove(toParent: self)
        }
    }
}
