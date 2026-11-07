import Foundation
import Security

enum KeychainConfigError: LocalizedError {
    case missingAccessGroup
    case unexpectedStatus(OSStatus)
    case invalidData

    var errorDescription: String? {
        switch self {
        case .missingAccessGroup:
            return "Не настроена общая Keychain-группа"
        case .unexpectedStatus(let status):
            return "Ошибка Keychain: \(status)"
        case .invalidData:
            return "Конфигурация в Keychain повреждена"
        }
    }
}

struct KeychainConfigStore {
    private let service = "com.inoagent.vpn.config"

    private var accessGroup: String? {
        get throws {
            Bundle.main.object(forInfoDictionaryKey: "KeychainAccessGroup") as? String
        }
    }

    func save(_ config: String, account: String) throws {
        let group = try accessGroup
        var base: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        if let group = group { base[kSecAttrAccessGroup as String] = group }
        let update = SecItemUpdate(base as CFDictionary, [kSecValueData as String: Data(config.utf8)] as CFDictionary)
        if update == errSecSuccess { return }
        guard update == errSecItemNotFound else { throw KeychainConfigError.unexpectedStatus(update) }
        var item = base
        item[kSecValueData as String] = Data(config.utf8)
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(item as CFDictionary, nil)
        guard status == errSecSuccess else { throw KeychainConfigError.unexpectedStatus(status) }
    }

    func load(account: String) throws -> String {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        if let group = try accessGroup { query[kSecAttrAccessGroup as String] = group }
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else { throw KeychainConfigError.unexpectedStatus(status) }
        guard let data = result as? Data, let value = String(data: data, encoding: .utf8) else {
            throw KeychainConfigError.invalidData
        }
        return value
    }

    func delete(account: String) throws {
        var query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        if let group = try accessGroup { query[kSecAttrAccessGroup as String] = group }
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainConfigError.unexpectedStatus(status)
        }
    }
}

