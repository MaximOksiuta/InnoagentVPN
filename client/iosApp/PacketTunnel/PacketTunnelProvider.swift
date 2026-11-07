import Foundation
import NetworkExtension
import OSLog
import WireGuardKit

final class PacketTunnelProvider: NEPacketTunnelProvider {
    private let logger = Logger(subsystem: "com.inoagent.vpn.ios", category: "PacketTunnel")
    private lazy var adapter = WireGuardAdapter(with: self) { [weak self] level, message in
        switch level {
        case .error: self?.logger.error("AmneziaWG: \(message, privacy: .private)")
        case .verbose: self?.logger.debug("AmneziaWG: \(message, privacy: .private)")
        }
    }

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        do {
            guard let tunnelProtocol = protocolConfiguration as? NETunnelProviderProtocol,
                  let account = tunnelProtocol.providerConfiguration?["configAccount"] as? String else {
                throw AWGConfigParserError.missingValue("configAccount")
            }
            let rawConfig = try KeychainConfigStore().load(account: account)
            let name = (tunnelProtocol.providerConfiguration?["profileName"] as? String) ?? "Inoagent"
            let configuration = try AWGConfigParser.parse(rawConfig, name: name)
            adapter.start(tunnelConfiguration: configuration, completionHandler: completionHandler)
        } catch {
            logger.error("Tunnel startup failed: \(error.localizedDescription, privacy: .private)")
            completionHandler(error)
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        adapter.stop { [weak self] error in
            if let error = error {
                self?.logger.error("Tunnel stop failed: \(error.localizedDescription, privacy: .private)")
            }
            completionHandler()
        }
    }

    override func handleAppMessage(_ messageData: Data, completionHandler: ((Data?) -> Void)? = nil) {
        guard messageData == Data([0]) else {
            completionHandler?(nil)
            return
        }
        adapter.getRuntimeConfiguration { configuration in
            completionHandler?(configuration?.data(using: .utf8))
        }
    }
}
