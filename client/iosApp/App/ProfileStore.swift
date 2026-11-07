import Combine
import Foundation
import Shared

@MainActor
final class ProfileStore: ObservableObject {
    @Published private(set) var profiles: [VPNProfileRecord] = []
    @Published var selectedProfileID: UUID?
    @Published var alertText: String?
    @Published var activeServiceUserID: Int64?
    let vpn = VPNManager()

    private let profilesKey = "vpnProfiles.v1"
    private let selectionKey = "selectedProfile.v1"
    private let keychain = KeychainConfigStore()
    private var cancellables = Set<AnyCancellable>()

    var selectedProfile: VPNProfileRecord? {
        visibleProfiles.first { $0.id == selectedProfileID } ?? visibleProfiles.first
    }
    var visibleProfiles: [VPNProfileRecord] { profiles.filter { $0.serviceUserID == nil || $0.serviceUserID == activeServiceUserID } }

    func saveServiceConfiguration(userID: Int64, serverID: Int64, name: String, contents: String) throws -> VPNProfileRecord {
        let metadata = try SharedConfigFacade().parseMetadata(fileName: "\(name).conf", contents: contents)
        let existing = profiles.first { $0.serviceUserID == userID && $0.serviceServerID == serverID }
        let id = existing?.id ?? UUID()
        let account = existing?.configAccount ?? "profile.\(id.uuidString.lowercased())"
        try keychain.save(contents, account: account)
        let record = VPNProfileRecord(id: id, name: name, endpoint: metadata.endpoint, configAccount: account, createdAt: existing?.createdAt ?? Date(), serviceUserID: userID, serviceServerID: serverID)
        profiles.removeAll { $0.id == id }; profiles.append(record); selectedProfileID = id
        persistMetadata()
        return record
    }

    init() {
        loadMetadata()
        vpn.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
    }

    func importConfiguration(from url: URL) {
        let hasAccess = url.startAccessingSecurityScopedResource()
        defer { if hasAccess { url.stopAccessingSecurityScopedResource() } }

        do {
            let data = try Data(contentsOf: url, options: [.mappedIfSafe])
            guard data.count <= 512 * 1024 else { throw ImportError.fileTooLarge }
            guard let contents = String(data: data, encoding: .utf8) else { throw ImportError.invalidEncoding }

            let metadata = try SharedConfigFacade().parseMetadata(
                fileName: url.lastPathComponent,
                contents: contents
            )
            let id = UUID()
            let account = "profile.\(id.uuidString.lowercased())"
            try keychain.save(contents, account: account)

            let profile = VPNProfileRecord(
                id: id,
                name: metadata.suggestedName,
                endpoint: metadata.endpoint,
                configAccount: account,
                createdAt: Date()
            )
            profiles.append(profile)
            profiles.sort { $0.createdAt > $1.createdAt }
            selectedProfileID = id
            persistMetadata()
        } catch {
            alertText = error.localizedDescription
        }
    }

    func select(_ profile: VPNProfileRecord) {
        selectedProfileID = profile.id
        persistMetadata()
    }

    func delete(_ profile: VPNProfileRecord) {
        if selectedProfileID == profile.id, vpn.phase != .disconnected { vpn.disconnect() }
        do {
            try keychain.delete(account: profile.configAccount)
            profiles.removeAll { $0.id == profile.id }
            selectedProfileID = profiles.first?.id
            persistMetadata()
        } catch {
            alertText = error.localizedDescription
        }
    }

    func toggleConnection() {
        guard let profile = selectedProfile else {
            alertText = "Сначала добавьте конфигурацию AmneziaWG"
            return
        }
        Task {
            do { try await vpn.toggle(profile: profile) }
            catch { alertText = error.localizedDescription }
        }
    }

    func dismissAlert() {
        alertText = nil
    }

    private func loadMetadata() {
        if let data = UserDefaults.standard.data(forKey: profilesKey),
           let decoded = try? JSONDecoder().decode([VPNProfileRecord].self, from: data) {
            profiles = decoded
        }
        if let rawID = UserDefaults.standard.string(forKey: selectionKey) {
            selectedProfileID = UUID(uuidString: rawID)
        }
        if selectedProfile == nil { selectedProfileID = profiles.first?.id }
    }

    private func persistMetadata() {
        if let data = try? JSONEncoder().encode(profiles) {
            UserDefaults.standard.set(data, forKey: profilesKey)
        }
        UserDefaults.standard.set(selectedProfileID?.uuidString, forKey: selectionKey)
    }
}

private enum ImportError: LocalizedError {
    case fileTooLarge
    case invalidEncoding

    var errorDescription: String? {
        switch self {
        case .fileTooLarge: return "Файл конфигурации больше 512 КБ"
        case .invalidEncoding: return "Конфигурация должна быть текстовым UTF-8 файлом"
        }
    }
}
