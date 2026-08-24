import SwiftUI

/// 开源许可页面：列出本项目使用的开源组件及其许可。
struct LicensesView: View {
    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 6) {
                    Text("LightNovelReader（上游 Android 项目）")
                        .font(.headline)
                    Text("© dmzz-yyhyy 与全体贡献者")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("特别鸣谢：本项目深受该项目的启发与指引，没有它就没有本项目。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text("Apache License 2.0")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if let link = URL(string: "https://github.com/dmzz-yyhyy/LightNovelReader") {
                        Link(destination: link) {
                            HStack(spacing: 4) {
                                Text("查看主页")
                                Image(systemName: "arrow.up.right")
                                    .font(.caption2)
                            }
                            .font(.caption)
                        }
                    }
                }
                .padding(.vertical, 4)
            }

            licenseSection(
                title: "霞鹜文楷 LXGW WenKai",
                author: "© 2023 LXGW",
                license: "SIL Open Font License 1.1",
                url: "https://github.com/lxgw/LxgwWenKai"
            )
            licenseSection(
                title: "思源宋体 Noto Serif SC",
                author: "© Adobe / Google",
                license: "SIL Open Font License 1.1",
                url: "https://github.com/google/fonts"
            )
        }
        .navigationTitle("开源许可")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func licenseSection(title: String, author: String, license: String, url: String) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                Text(title).font(.headline)
                Text(author).font(.caption).foregroundStyle(.secondary)
                Text(license).font(.caption).foregroundStyle(.secondary)
                if let link = URL(string: url) {
                    Link(destination: link) {
                        HStack(spacing: 4) {
                            Text("查看主页")
                            Image(systemName: "arrow.up.right")
                                .font(.caption2)
                        }
                        .font(.caption)
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }
}
