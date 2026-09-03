import SwiftUI

struct ReadingView: View {
    @EnvironmentObject private var store: AppStore
    @State private var readerConfig: ReaderConfig?

    private var recentBooks: [Book] {
        store.recentBooks
    }

    var body: some View {
        Group {
            if recentBooks.isEmpty {
                emptyState
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 24) {
                        sectionHeader("最近阅读")

                        ForEach(recentBooks) { book in
                            ReadingRow(book: book) {
                                readerConfig = ReaderConfig(book: book, chapter: book.lastChapter)
                            } onClearUpdate: {
                                store.clearUpdateFlag(for: book.id)
                            }
                        }
                    }
                    .padding()
                }
            }
        }
        .navigationTitle("阅读")
        .fullScreenCover(item: $readerConfig) { config in
            ReaderView(book: config.book, startChapter: config.chapter)
        }
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "book")
                .font(.system(size: 40))
                .foregroundStyle(.tertiary)
            Text("还没有开始阅读")
                .font(.subheadline)
                .fontWeight(.medium)
            Text("去「探索」或「书架」挑一本书，开始你的异世界之旅吧")
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
        .frame(maxWidth: .infinity)
    }

    private func sectionHeader(_ title: String) -> some View {
        HStack {
            Text(title).font(.headline)
            Spacer()
        }
    }
}

private struct ReadingRow: View {
    let book: Book
    let onContinue: () -> Void
    var onClearUpdate: () -> Void = {}

    /// 第一小行：作者 · 连载状态
    private var bookSubtitle: String {
        let status = book.isCompleted == true ? "已完结" : (book.isCompleted == false ? "连载中" : "")
        let author = book.author.trimmingCharacters(in: .whitespaces)
        if author.isEmpty { return status }
        if status.isEmpty { return author }
        return "\(author) · \(status)"
    }

    var body: some View {
        HStack(spacing: 14) {
            NavigationLink {
                BookDetailView(book: book)
            } label: {
                HStack(spacing: 14) {
                    BookCoverView(book: book, showsProgress: false, showsUpdateDot: book.hasUpdate, shadowRadius: 0)
                        .frame(width: 64, height: 86)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(book.title)
                            .font(.body)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                        Text(bookSubtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        ProgressView(value: book.progress)
                            .tint(.accentPurple)
                            .frame(maxWidth: 180, alignment: .leading)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            // 点进最近阅读的书：若该书有更新提醒，视为已知晓并清除
            .simultaneousGesture(TapGesture().onEnded {
                if book.hasUpdate {
                    onClearUpdate()
                }
            })

            Spacer(minLength: 4)

            Button(action: onContinue) {
                Image(systemName: "play.fill")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(Color.accentPurple, in: Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}
