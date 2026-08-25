import SwiftUI

/// 应用内开屏遮罩：与 LaunchScreen.storyboard 视觉一致（米白背景 + 底部中央图标 + 文字）。
/// 图标/文字固定在底部（bottom 60），与 storyboard 的约束完全对齐，避免切换时闪动。
/// 「for ios」后缀使用系统内置花体 SnellRoundhand，与 storyboard 中的字体保持一致。
struct SplashView: View {
    var body: some View {
        VStack {
            Spacer()
            Image("LaunchIconRounded")
                .resizable()
                .scaledToFit()
                .frame(width: 88, height: 88)
            HStack(alignment: .firstTextBaseline, spacing: 2) {
                Text("LightNovelReader")
                    .font(.system(size: 18, weight: .medium))
                Text("for ios")
                    .font(.custom("SnellRoundhand", size: 17))
            }
            .foregroundStyle(Color(hex: 0x333333))
            .padding(.top, 12)
            .padding(.bottom, 60)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(hex: 0xFCF7EE))  // 与 LaunchScreen 的米白背景一致
        .ignoresSafeArea()
    }
}
