import SwiftUI

/// 与 LaunchScreen.storyboard 共用的几何尺寸。
/// 总高度固定为 184 pt，防止应用接管画面后因字体固有高度或 safe area 变化发生跳动。
enum SplashLayoutMetrics {
    static let iconSize: CGFloat = 88
    static let iconTitleSpacing: CGFloat = 12
    static let titleHeight: CGFloat = 24
    static let bottomInset: CGFloat = 60

    static let contentHeight = iconSize + iconTitleSpacing + titleHeight + bottomInset
}

/// 应用内开屏遮罩：与 LaunchScreen.storyboard 视觉一致（米白背景 + 底部中央图标 + 文字）。
/// 图标/文字固定在底部（bottom 60），与 storyboard 的约束完全对齐，避免切换时闪动。
/// 「for ios」后缀使用系统内置花体 SnellRoundhand，与 storyboard 中的字体保持一致。
struct SplashView: View {
    var body: some View {
        ZStack(alignment: .bottom) {
            Color(hex: 0xFCF7EE)
                .ignoresSafeArea()

            VStack(spacing: SplashLayoutMetrics.iconTitleSpacing) {
                Image("LaunchIconRounded")
                    .resizable()
                    .scaledToFit()
                    .frame(
                        width: SplashLayoutMetrics.iconSize,
                        height: SplashLayoutMetrics.iconSize
                    )

                HStack(alignment: .firstTextBaseline, spacing: 2) {
                    Text("LightNovelReader")
                        .font(.system(size: 18, weight: .medium))
                    Text("for ios")
                        .font(.custom("SnellRoundhand", size: 17))
                }
                .frame(height: SplashLayoutMetrics.titleHeight, alignment: .top)
                .foregroundStyle(Color(hex: 0x333333))
            }
            .padding(.bottom, SplashLayoutMetrics.bottomInset)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ignoresSafeArea()
    }
}
