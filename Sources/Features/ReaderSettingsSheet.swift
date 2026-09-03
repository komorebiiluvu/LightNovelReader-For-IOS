import SwiftUI

struct ReaderSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss

    @Binding var preferences: ReaderPreferences
    let systemIsDark: Bool

    @State private var detent: PresentationDetent = .medium
    /// 滑杆/步进器防抖：拖动过程中只更新本地值，停顿后才写回 preferences 触发重排，
    /// 避免每动一格就全量分页 + 全量 JSON 持久化（老设备上拖动行距滑杆会卡）
    @State private var pendingLineSpacing: CGFloat?
    @State private var pendingMarginLeft: CGFloat?
    @State private var pendingMarginRight: CGFloat?
    @State private var pendingMarginTop: CGFloat?
    @State private var pendingMarginBottom: CGFloat?
    @State private var debounceTask: Task<Void, Never>?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    section("背景主题") {
                        HStack(spacing: 8) {
                            followSystemSwatch
                            ForEach(Array(readerBackgrounds.enumerated()), id: \.offset) { index, bg in
                                Button {
                                    preferences.backgroundIndex = index
                                } label: {
                                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                                        .fill(bg.background)
                                        .frame(width: 44, height: 44)
                                        .overlay {
                                            Text(bg.name)
                                                .font(.system(size: 10))
                                                .foregroundStyle(bg.foreground)
                                        }
                                        .overlay {
                                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                                .stroke(preferences.backgroundIndex == index ? Color.accentPurple : .clear, lineWidth: 2)
                                        }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    section("字体") {
                        HStack(spacing: 8) {
                            ForEach(ReaderFontFamily.available) { family in
                                Button {
                                    preferences.fontFamily = family
                                } label: {
                                    Text(family.rawValue)
                                        .font(family.resolvedName.map { Font.custom($0, size: 16) } ?? .system(size: 16))
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 9)
                                        .background(
                                            preferences.fontFamily == family
                                                ? Color.accentPurple.opacity(0.16)
                                                : Color(uiColor: .secondarySystemBackground),
                                            in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        )
                                        .foregroundStyle(preferences.fontFamily == family ? Color.accentPurple : Color.secondary)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    section("字重") {
                        HStack(spacing: 10) {
                            optionButton("常规", selected: !preferences.bold) { preferences.bold = false }
                            optionButton("加粗", selected: preferences.bold) { preferences.bold = true }
                        }
                    }

                    section("字号") {
                        HStack(spacing: 18) {
                            stepButton("-") { adjustFontSize(-1) }
                            Text("\(Int(preferences.fontSize))pt")
                                .font(.headline)
                                .frame(minWidth: 54)
                            stepButton("+") { adjustFontSize(1) }
                        }
                    }

                    section("行距") {
                        sliderRow("行距", value: debouncedBinding(get: { pendingLineSpacing ?? preferences.lineSpacing }, set: { pendingLineSpacing = $0; scheduleDebouncedCommit() }), range: 0...20, step: 1)
                    }

                    section("边距") {
                        sliderRow("左边距", value: debouncedBinding(get: { pendingMarginLeft ?? preferences.marginLeft }, set: { pendingMarginLeft = $0; scheduleDebouncedCommit() }), range: 0...80, step: 2)
                        sliderRow("右边距", value: debouncedBinding(get: { pendingMarginRight ?? preferences.marginRight }, set: { pendingMarginRight = $0; scheduleDebouncedCommit() }), range: 0...80, step: 2)
                        sliderRow("上边距", value: debouncedBinding(get: { pendingMarginTop ?? preferences.marginTop }, set: { pendingMarginTop = $0; scheduleDebouncedCommit() }), range: 0...120, step: 2)
                        sliderRow("下边距", value: debouncedBinding(get: { pendingMarginBottom ?? preferences.marginBottom }, set: { pendingMarginBottom = $0; scheduleDebouncedCommit() }), range: 0...120, step: 2)
                    }

                    section("翻页方式") {
                        HStack(spacing: 10) {
                            ForEach(ReaderMode.allCases) { item in
                                optionButton(item.rawValue, selected: preferences.mode == item) {
                                    preferences.mode = item
                                }
                            }
                        }
                    }
                }
                .padding()
                .padding(.bottom, 56)
            }
            .navigationTitle("阅读设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
            .overlay(alignment: .bottom) {
                if detent == .medium {
                    Button {
                        withAnimation(.easeInOut(duration: 0.35)) {
                            detent = .large
                        }
                    } label: {
                        Image(systemName: "chevron.down")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.accentPurple)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(Color(uiColor: .systemBackground)))
                            .shadow(color: .black.opacity(0.18), radius: 6, y: 2)
                    }
                    .padding(.bottom, 10)
                    .transition(.opacity)
                }
            }
        }
        .presentationDetents([.medium, .large], selection: $detent)
    }

    /// 「跟随系统」色块：配色随系统明暗实时变化（浅色=白底黑字，深色=黑底白字）
    private var followSystemSwatch: some View {
        let dark = systemIsDark
        return Button {
            preferences.backgroundIndex = ReaderView.followSystemBackgroundIndex
        } label: {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(dark ? Color.black : Color.white)
                .frame(width: 44, height: 44)
                .overlay {
                    Text("跟随系统")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(dark ? Color.white : Color.black)
                        .minimumScaleFactor(0.7)
                }
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(dark ? Color.white.opacity(0.25) : Color.black.opacity(0.25), lineWidth: 1)
                )
                .overlay {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(preferences.backgroundIndex == ReaderView.followSystemBackgroundIndex ? Color.accentPurple : .clear, lineWidth: 2)
                }
        }
        .buttonStyle(.plain)
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.headline)
            content()
        }
    }

    private func optionButton(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(
                    selected
                        ? Color.accentPurple.opacity(0.16)
                        : Color(uiColor: .secondarySystemBackground),
                    in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                )
                .foregroundStyle(selected ? Color.accentPurple : Color.secondary)
        }
        .buttonStyle(.plain)
    }

    private func sliderRow(_ title: String, value: Binding<CGFloat>, range: ClosedRange<CGFloat>, step: CGFloat) -> some View {
        HStack(spacing: 12) {
            Text(title)
                .font(.subheadline)
                .frame(width: 52, alignment: .leading)
            Slider(value: value, in: range, step: step)
            Text("\(Int(value.wrappedValue))")
                .font(.caption.monospacedDigit())
                .frame(width: 32, alignment: .trailing)
                .foregroundStyle(.secondary)
        }
    }

    /// 把防抖中的本地值与已生效值合并成展示用的 Binding
    private func debouncedBinding(get: @escaping () -> CGFloat, set: @escaping (CGFloat) -> Void) -> Binding<CGFloat> {
        Binding(get: get, set: set)
    }

    /// 拖动停顿 300ms 后把本地待写值一次性落回 preferences（触发一次分页）
    private func scheduleDebouncedCommit() {
        debounceTask?.cancel()
        debounceTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                if let v = pendingLineSpacing { preferences.lineSpacing = v; pendingLineSpacing = nil }
                if let v = pendingMarginLeft { preferences.marginLeft = v; pendingMarginLeft = nil }
                if let v = pendingMarginRight { preferences.marginRight = v; pendingMarginRight = nil }
                if let v = pendingMarginTop { preferences.marginTop = v; pendingMarginTop = nil }
                if let v = pendingMarginBottom { preferences.marginBottom = v; pendingMarginBottom = nil }
            }
        }
    }

    /// 字号步进：长按连点时同样防抖（300ms），避免连点 N 次触发 N 次分页
    private func adjustFontSize(_ delta: CGFloat) {
        debounceTask?.cancel()
        debounceTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                preferences.fontSize = min(max(13, preferences.fontSize + delta), 28)
            }
        }
    }

    private func stepButton(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.headline)
                .frame(width: 38, height: 38)
                .background(Color(uiColor: .secondarySystemBackground), in: Circle())
        }
        .buttonStyle(.plain)
    }
}
