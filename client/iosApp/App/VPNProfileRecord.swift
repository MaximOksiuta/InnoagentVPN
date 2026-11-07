import Foundation

struct VPNProfileRecord: Codable, Identifiable, Hashable {
    let id: UUID
    var name: String
    let endpoint: String
    let configAccount: String
    let createdAt: Date
    var serviceUserID: Int64? = nil
    var serviceServerID: Int64? = nil
}

struct TrafficSnapshot: Equatable {
    var receivedBytes: UInt64 = 0
    var sentBytes: UInt64 = 0

    static let zero = TrafficSnapshot()
}

enum ConnectionPhase: Equatable {
    case disconnected
    case connecting
    case connected
    case disconnecting
    case failed(String)

    var title: String {
        switch self {
        case .disconnected: return "Не подключено"
        case .connecting: return "Подключаемся"
        case .connected: return "Подключено"
        case .disconnecting: return "Отключаемся"
        case .failed: return "Ошибка подключения"
        }
    }

    var isBusy: Bool {
        self == .connecting || self == .disconnecting
    }
}
