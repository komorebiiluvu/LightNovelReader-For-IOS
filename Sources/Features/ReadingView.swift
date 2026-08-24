import SwiftUI

struct ReadingView: View {
    @EnvironmentObject private var store: AppStore
    @State private var readerConfig: ReaderConfig?

    private var recentBooks: [Book] {
        store.recentBooks
    }

    private var updatedBooks: [Book] {
        store.updatedBooks
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
                            }
                        }

                        if !updatedBooks.isEmpty {
                            sectionHeader("更新提醒")

                            ForEach(updatedBooks) { book in
                                updateRow(book)
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

    private func updateRow(_ book: Book) -> some View {
        NavigationLink {
            BookDetailView(book: book)
        } label: {
            HStack(spacing: 12) {
                BookCoverView(book: book, showsProgress: false, shadowRadius: 0)
                    .frame(width: 38, height: 50)
                VStack(alignment: .leading, spacing: 3) {
                    Text(book.title)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text("\(book.author) · 更新至第 \(book.totalChapters) 章")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Circle().fill(.yellow).frame(width: 7, height: 7)
            }
            .padding(12)
            .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
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
                    BookCoverView(book: book, showsProgress: false, shadowRadius: 0)
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
