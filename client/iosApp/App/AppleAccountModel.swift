import SwiftUI
import Shared
#if os(iOS)
import UIKit
#endif

@MainActor final class AppleAccountModel: ObservableObject {
    @Published private(set) var user: SiteUser?
    @Published private(set) var servers: [SiteServer] = []
    @Published private(set) var notifications: [SiteNotification] = []
    @Published private(set) var statuses: [SiteServerStatus] = []
    @Published var selectedServerID: Int64?
    @Published private(set) var busy = false
    @Published private(set) var activity = ""
    @Published var message: String?
    @Published var exportURL: URL?
    let api = InoagentApi()
    private let keychain = KeychainConfigStore()
    private let profiles: ProfileStore
    private var polling: Task<Void, Never>?
    private var forceConfigurationFetch = false
    private var refreshing = false

    init(profiles: ProfileStore) {
        self.profiles = profiles
        Task {
            if let token = try? keychain.load(account: "service.token") {
                api.setToken(value: token)
                if let raw = try? keychain.load(account: "service.cached-account"), let cached = try? ServiceCacheCodec().decode(value: raw) {
                    user = cached.user; servers = cached.servers; selectedServerID = cached.servers.first?.id; profiles.activeServiceUserID = cached.user.id
                }
                await refresh()
            }
        }
        polling = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(30))
                guard let self = self, !Task.isCancelled else { return }
                if self.user != nil && !self.busy {
                    do { self.notifications = try await self.api.notifications() }
                    catch { /* A missed inbox refresh must not disconnect the VPN. */ }
                }
            }
        }
    }
    deinit { polling?.cancel() }
    func artwork(_ server: SiteServer) -> URL? { URL(string: api.artworkUrl(serverId: server.id, name: server.name)) }
    func login(phone: String, password: String) async {
        guard !busy else { return }
        busy = true; activity = "Входим в аккаунт"; message = nil
        defer { busy = false }
        do {
            let token = try await api.login(phone: phone, password: password)
            let current = try await api.me()
            try keychain.save(token, account: "service.token")
            user = current; profiles.activeServiceUserID = current.id
            try await loadData()
        } catch { message = error.localizedDescription }
    }
    func register(phone: String, password: String, nickname: String) async {
        guard !busy else { return }
        busy = true; activity = "Создаём аккаунт"; message = nil
        defer { busy = false }
        do { try await api.register(phone: phone, password: password, nickname: nickname); message = "Аккаунт создан. Дождитесь подтверждения администратора и войдите" }
        catch { message = error.localizedDescription }
    }
    func refresh() async {
        guard !busy && !refreshing else { return }
        refreshing = true
        let blocksUI = user == nil
        if blocksUI { busy = true }
        activity = "Обновляем серверы"
        defer { refreshing = false; if blocksUI { busy = false } }
        do { try await loadData() } catch { message = error.localizedDescription }
    }
    private func loadData() async throws {
        let current = try await api.me()
        let available = try await api.servers()
        let inbox = try await api.notifications()
        statuses = (try? await api.statuses()) ?? []
        user = current; profiles.activeServiceUserID = current.id; servers = available; notifications = inbox
        try keychain.save(ServiceCacheCodec().encode(user: current, servers: available), account: "service.cached-account")
        if !available.contains(where: { $0.id == selectedServerID }) { selectedServerID = available.first?.id }
    }
    func logout() {
        guard !busy else { return }
        if profiles.vpn.phase != .disconnected { profiles.vpn.disconnect() }
        try? keychain.delete(account: "service.token"); try? keychain.delete(account: "service.cached-account"); api.setToken(value: "")
        user = nil; servers = []; notifications = []; selectedServerID = nil; profiles.activeServiceUserID = nil
        if let url = exportURL { try? FileManager.default.removeItem(at: url) }; exportURL = nil
    }
    func markRead(_ row: SiteNotification) async {
        do { try await api.readNotification(id: row.id); notifications = try await api.notifications() }
        catch { message = error.localizedDescription }
    }
    func connect() async {
        guard !busy else { return }
        #if !COMPANION
        if profiles.vpn.phase == .connected { profiles.vpn.disconnect(); return }
        #endif
        guard let user = user, let server = servers.first(where: { $0.id == selectedServerID }) else { message = "Войдите и выберите сервер"; return }
        busy = true; activity = "Выпускаем или загружаем конфигурацию"; message = nil
        defer { busy = false }
        do {
            if !forceConfigurationFetch, let cached = profiles.profiles.first(where: { $0.serviceUserID == user.id && $0.serviceServerID == server.id }) {
                let contents = try keychain.load(account: cached.configAccount)
                #if COMPANION
                try export(contents, serverID: server.id)
                #else
                try await profiles.vpn.connect(profile: cached)
                #endif
                return
            }
            let defaults = UserDefaults.standard
            let key = "service.device.\(user.id)"
            let bound = (defaults.object(forKey: key) as? NSNumber)?.int64Value
            let installation = defaults.string(forKey: "service.installation") ?? UUID().uuidString
            defaults.set(installation, forKey: "service.installation")
            #if os(macOS)
            let deviceName = "Mac · \(installation.prefix(6))"
            #else
            let deviceName = "\(UIDevice.current.model) · \(installation.prefix(6))"
            #endif
            let devices = try await api.devices()
            let device: SiteDevice
            if let existing = devices.first(where: { $0.id == bound || $0.name == deviceName }) { device = existing }
            else { device = try await api.createDevice(name: deviceName) }
            defaults.set(NSNumber(value: device.id), forKey: key)
            let config = try await api.configuration(deviceId: device.id, serverId: server.id, requestKey: "apple-\(UUID().uuidString)")
            let profile = try profiles.saveServiceConfiguration(userID: user.id, serverID: server.id, name: server.name, contents: config.config)
            forceConfigurationFetch = false
            #if COMPANION
            try export(config.config, serverID: server.id)
            #else
            try await profiles.vpn.connect(profile: profile)
            #endif
        } catch { message = error.localizedDescription }
    }
    private func export(_ contents: String, serverID: Int64) throws {
        if let previous = exportURL { try? FileManager.default.removeItem(at: previous) }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("inoagent-\(serverID).conf")
        try contents.write(to: url, atomically: true, encoding: .utf8)
        try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: url.path)
        exportURL = url
    }
    func refreshConfiguration() async { await refresh(); forceConfigurationFetch = true }
}
