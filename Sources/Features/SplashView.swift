import SwiftUI

/// 应用内开屏遮罩：与 LaunchScreen.storyboard 视觉一致（米白背景 + 底部中央图标 + 文字）。
/// 图标/文字固定在底部（bottom 60），与 storyboard 的约束完全对齐，避免切换时闪动。
struct SplashView: View {
    var body: some View {
        VStack {
            Spacer()
            Image("LaunchIconRounded")
                .resizable()
                .scaledToFit()
                .frame(width: 88, height: 88)
            Text("LightNovelReader")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color(hex: 0x333333))
                .padding(.top, 12)
                .padding(.bottom, 60)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(hex: 0xFCF7EE))  // 与 LaunchScreen 的米白背景一致
        .ignoresSafeArea()
    }
}
