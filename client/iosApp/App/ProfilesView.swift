import SwiftUI
import UniformTypeIdentifiers

struct ProfilesView: View {
    @EnvironmentObject private var store: ProfileStore
    @State private var showsImporter = false

    var body: some View {
        NavigationStack {
            Group {
                if store.profiles.isEmpty {
                    ContentUnavailableView {
                        Label("Нет конфигураций", systemImage: "doc.badge.plus")
                    } description: {
                        Text("Добавьте файл .conf, полученный на вашем сайте")
                    } actions: {
                        Button("Выбрать файл") { showsImporter = true }
                            .buttonStyle(.borderedProminent)
                    }
                } else {
                    List {
                        Section("AmneziaWG") {
                            ForEach(store.profiles) { profile in
                                ProfileRow(
                                    profile: profile,
                                    isSelected: store.selectedProfile?.id == profile.id
                                )
                                .contentShape(Rectangle())
                                .onTapGesture { store.select(profile) }
                                .swipeActions {
                                    Button("Удалить", role: .destructive) { store.delete(profile) }
                                }
                                .accessibilityAddTraits(
                                    store.selectedProfile?.id == profile.id ? .isSelected : []
                                )
                            }
                        }

                        Section {
                            Label("Приватные ключи хранятся в Keychain и доступны только приложению и VPN-туннелю.", systemImage: "lock.shield")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Конфигурации")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showsImporter = true } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Добавить конфигурацию")
                }
            }
            .fileImporter(
                isPresented: $showsImporter,
                allowedContentTypes: [.amneziaWGConfiguration, .plainText],
                allowsMultipleSelection: false
            ) { result in
                switch result {
                case .success(let urls):
                    if let url = urls.first { store.importConfiguration(from: url) }
                case .failure(let error):
                    store.alertText = error.localizedDescription
                }
            }
        }
    }
}

private struct ProfileRow: View {
    let profile: VPNProfileRecord
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "server.rack")
                .foregroundStyle(isSelected ? .black : Color("BrandMint"))
                .frame(width: 38, height: 38)
                .background(
                    isSelected ? Color("BrandMint") : Color("BrandMint").opacity(0.12),
                    in: RoundedRectangle(cornerRadius: 11, style: .continuous)
                )
            VStack(alignment: .leading, spacing: 3) {
                Text(profile.name).font(.headline)
                Text(profile.endpoint)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            if isSelected {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(Color("BrandMint"))
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, 5)
        .accessibilityElement(children: .combine)
    }
}

private extension UTType {
    static let amneziaWGConfiguration = UTType(filenameExtension: "conf") ?? .plainText
}
