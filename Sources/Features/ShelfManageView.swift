import SwiftUI

struct ShelfManageView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    @State private var newName = ""
    @State private var renamingShelf: BookShelf?
    @State private var renameText = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 10) {
                        TextField("新书架名称", text: $newName)
                        Button("新建") {
                            store.addShelf(named: newName)
                            newName = ""
                        }
                        .disabled(newName.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                } header: {
                    Text("新建书架")
                }

                Section {
                    if store.shelves.isEmpty {
                        Text("还没有自定义书架")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    ForEach(store.shelves) { shelf in
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(shelf.name)
                                Text("\(shelf.bookIDs.count) 本")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            if store.selectedShelfID == shelf.id {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(Color.accentPurple)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            store.selectedShelfID = shelf.id
                        }
                        .swipeActions {
                            Button(role: .destructive) {
                                store.deleteShelf(id: shelf.id)
                            } label: {
                                Label("删除", systemImage: "trash")
                            }
                            Button {
                                renamingShelf = shelf
                                renameText = shelf.name
                            } label: {
                                Label("重命名", systemImage: "pencil")
                            }
                        }
                    }
                } header: {
                    Text("我的书架")
                } footer: {
                    Text("删除书架不会移除书籍本身，书会保留在书源书目与默认书架中。")
                }
            }
            .navigationTitle("书架管理")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
            .alert("重命名书架", isPresented: Binding(
                get: { renamingShelf != nil },
                set: { if !$0 { renamingShelf = nil } }
            )) {
                TextField("书架名称", text: $renameText)
                Button("保存") {
                    if let shelf = renamingShelf {
                        store.renameShelf(id: shelf.id, to: renameText)
                    }
                    renamingShelf = nil
                }
                Button("取消", role: .cancel) {}
            }
        }
    }
}
