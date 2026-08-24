#!/bin/bash
# ============================================================================
# SharedKit.xcframework 构建脚本（KMP 数据互通层）
#
# 用途：把 shared/ 下的 Kotlin Multiplatform 模块编译成 SharedKit.xcframework，
#       供 iOS 工程通过 KmpBookSourceAdapter 使用（与上游 Android 版数据互通）。
#
# 前置条件（本机当前未装，需要先安装）：
#   1. JDK 17+    → brew install --cask zulu@17   或  openjdk@17
#   2. Gradle     → 脚本会用 ./gradlew（首次自动下载 Gradle 8.13）
#   3. Xcode      → 已装（Kotlin/Native 需要 Xcode 工具链）
#
# 产物：build/bin/SharedKit.xcframework（含 iosArm64 + iosSimulatorArm64）
# 用法：
#   ./scripts/build-sharedkit.sh          # 全量构建 + 合并 xcframework
# ============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
SHARED_DIR="$ROOT_DIR/shared"
OUT_DIR="$SHARED_DIR/build/bin"

echo "==> 检查工具链..."

# 1. JDK 检查
if ! /usr/libexec/java_home -v 17+ &>/dev/null; then
  echo "❌ 未找到 JDK 17+。请先安装："
  echo "   brew install --cask zulu@17"
  echo "   或（macOS 自带）：brew install openjdk@17"
  exit 1
fi
echo "   ✓ JDK 17+ 已就绪"

# 2. Gradle wrapper
if [ ! -f "$SHARED_DIR/gradlew" ]; then
  echo "==> 生成 Gradle wrapper（首次需联网下载 Gradle 8.13）..."
  (cd "$SHARED_DIR" && gradle wrapper --gradle-version 8.13)
fi

# 3. 编译 iOS framework
echo "==> 编译 iosArm64（真机）..."
(cd "$SHARED_DIR" && ./gradlew linkDebugFrameworkIosArm64 --no-daemon)

echo "==> 编译 iosSimulatorArm64（模拟器）..."
(cd "$SHARED_DIR" && ./gradlew linkDebugFrameworkIosSimulatorArm64 --no-daemon)

# 4. 合并 xcframework
echo "==> 合并 SharedKit.xcframework ..."
IOS_ARM64="$SHARED_DIR/build/bin/iosArm64/debugFramework/SharedKit.framework"
IOS_SIM="$SHARED_DIR/build/bin/iosSimulatorArm64/debugFramework/SharedKit.framework"

rm -rf "$OUT_DIR/SharedKit.xcframework"
xcodebuild -create-xcframework \
  -framework "$IOS_ARM64" \
  -framework "$IOS_SIM" \
  -output "$OUT_DIR/SharedKit.xcframework"

echo ""
echo "✅ 构建完成：$OUT_DIR/SharedKit.xcframework"
echo ""
echo "下一步：把 SharedKit.xcframework 拖进 Xcode 工程（Embed & Sign），"
echo "       或在 project.yml 的 targets 里加 sdk 引用后重新生成工程。"
echo "       KmpBookSourceAdapter 会在编译进 SharedKit 后自动启用。"
