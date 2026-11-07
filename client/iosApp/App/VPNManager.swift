import Combine
import Foundation
import NetworkExtension

@MainActor
final class VPNManager: ObservableObject {
    @Published private(set) var phase: ConnectionPhase = .disconnected
    @Published private(set) var traffic: TrafficSnapshot = .zero
    @Published private(set) var connectedAt: Date?

    private var manager: NETunnelProviderManager?
    private var statusObserver: NSObjectProtocol?

    init() {
        statusObserver = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            guard let connection = notification.object as? NEVPNConnection else { return }
            Task { @MainActor [weak self] in self?.apply(connection.status) }
        }

        #if !COMPANION
        Task { await restore() }
        #endif
    }

    deinit {
        if let statusObserver = statusObserver {
            NotificationCenter.default.removeObserver(statusObserver)
        }
    }

    func toggle(profile: VPNProfileRecord) async throws {
        switch phase {
        case .connected, .connecting:
            disconnect()
        case .disconnected, .disconnecting, .failed:
            try await connect(profile: profile)
        }
    }

    func connect(profile: VPNProfileRecord) async throws {
        phase = .connecting
        do {
            let activeManager = try await loadOrCreateManager()
            let tunnelProtocol = NETunnelProviderProtocol()
            tunnelProtocol.providerBundleIdentifier = (Bundle.main.bundleIdentifier ?? "com.inoagent.vpn.ios") + ".tunnel"
            tunnelProtocol.serverAddress = profile.endpoint
            tunnelProtocol.providerConfiguration = [
                "configAccount": profile.configAccount,
                "profileName": profile.name,
            ]
            activeManager.localizedDescription = "Inoagent VPN — \(profile.name)"
            activeManager.protocolConfiguration = tunnelProtocol
            activeManager.isEnabled = true

            try await save(activeManager)
            try await reload(activeManager)
            manager = activeManager
            try activeManager.connection.startVPNTunnel()
        } catch {
            phase = .failed(error.localizedDescription)
            throw error
        }
    }

    func disconnect() {
        phase = .disconnecting
        manager?.connection.stopVPNTunnel()
    }

    func refreshTraffic() async {
        guard phase == .connected,
              let session = manager?.connection as? NETunnelProviderSession else { return }
        do {
            let data: Data? = try await withCheckedThrowingContinuation { continuation in
                do {
                    try session.sendProviderMessage(Data([0])) { response in
                        continuation.resume(returning: response)
                    }
                } catch {
                    continuation.resume(throwing: error)
                }
            }
            guard let data = data, let text = String(data: data, encoding: .utf8) else { return }
            traffic = Self.parseTraffic(text)
        } catch {
            // A missing runtime snapshot must not tear down a healthy tunnel.
        }
    }

    private func restore() async {
        do {
            let managers = try await loadManagers()
            manager = managers.first
            if let status = manager?.connection.status { apply(status) }
        } catch {
            phase = .failed(error.localizedDescription)
        }
    }

    private func loadOrCreateManager() async throws -> NETunnelProviderManager {
        if let manager = manager { return manager }
        let managers = try await loadManagers()
        let loaded = managers.first ?? NETunnelProviderManager()
        manager = loaded
        return loaded
    }

    private func loadManagers() async throws -> [NETunnelProviderManager] {
        try await withCheckedThrowingContinuation {
            (continuation: CheckedContinuation<[NETunnelProviderManager], Error>) in
            NETunnelProviderManager.loadAllFromPreferences { managers, error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: managers ?? [])
                }
            }
        }
    }

    private func save(_ manager: NETunnelProviderManager) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            manager.saveToPreferences { error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }

    private func reload(_ manager: NETunnelProviderManager) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            manager.loadFromPreferences { error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }

    private func apply(_ status: NEVPNStatus) {
        switch status {
        case .invalid, .disconnected:
            phase = .disconnected
            connectedAt = nil
            traffic = .zero
        case .connecting, .reasserting:
            phase = .connecting
        case .connected:
            if connectedAt == nil { connectedAt = Date() }
            phase = .connected
        case .disconnecting:
            phase = .disconnecting
        @unknown default:
            phase = .disconnected
        }
    }

    private static func parseTraffic(_ configuration: String) -> TrafficSnapshot {
        var snapshot = TrafficSnapshot.zero
        for line in configuration.split(whereSeparator: \.isNewline) {
            let pair = line.split(separator: "=", maxSplits: 1).map(String.init)
            guard pair.count == 2, let bytes = UInt64(pair[1]) else { continue }
            if pair[0] == "rx_bytes" { snapshot.receivedBytes += bytes }
            if pair[0] == "tx_bytes" { snapshot.sentBytes += bytes }
        }
        return snapshot
    }
}
