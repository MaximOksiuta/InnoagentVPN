import Foundation
import WireGuardKit

enum AWGConfigParserError: LocalizedError {
    case missingInterface
    case missingValue(String)
    case invalidValue(String, String)
    case unsupportedKey(String)

    var errorDescription: String? {
        switch self {
        case .missingInterface:
            return "В конфигурации отсутствует секция [Interface]"
        case .missingValue(let key):
            return "В конфигурации отсутствует обязательный параметр \(key)"
        case .invalidValue(let key, let value):
            return "Некорректное значение \(key): \(value)"
        case .unsupportedKey(let key):
            return "Параметр \(key) не поддерживается iOS-клиентом"
        }
    }
}

enum AWGConfigParser {
    private enum Section { case none, interface, peer }

    static func parse(_ text: String, name: String) throws -> TunnelConfiguration {
        var section = Section.none
        var interfaceValues: [String: String]?
        var current: [String: String] = [:]
        var peers: [[String: String]] = []

        func committed(_ section: Section, values: [String: String]) -> ([String: String]?, [[String: String]]) {
            switch section {
            case .interface: return (values, [])
            case .peer: return (nil, [values])
            case .none: return (nil, [])
            }
        }

        for rawLine in text.split(omittingEmptySubsequences: false, whereSeparator: \.isNewline) {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty, !line.hasPrefix("#"), !line.hasPrefix(";") else { continue }

            let lower = line.lowercased()
            if lower == "[interface]" || lower == "[peer]" {
                let result = committed(section, values: current)
                if let values = result.0 {
                    guard interfaceValues == nil else { throw AWGConfigParserError.invalidValue("Interface", "duplicate") }
                    interfaceValues = values
                }
                peers.append(contentsOf: result.1)
                current.removeAll(keepingCapacity: true)
                section = lower == "[interface]" ? .interface : .peer
                continue
            }

            guard section != .none, let split = line.firstIndex(of: "=") else {
                throw AWGConfigParserError.invalidValue("line", line)
            }
            let key = line[..<split].trimmingCharacters(in: .whitespaces).lowercased()
            let value = line[line.index(after: split)...].trimmingCharacters(in: .whitespaces)
            current[key] = current[key].map { "\($0),\(value)" } ?? value
        }

        let result = committed(section, values: current)
        if let values = result.0 {
            guard interfaceValues == nil else { throw AWGConfigParserError.invalidValue("Interface", "duplicate") }
            interfaceValues = values
        }
        peers.append(contentsOf: result.1)

        guard let interfaceValues else { throw AWGConfigParserError.missingInterface }
        let interface = try makeInterface(interfaceValues)
        let parsedPeers = try peers.map(makePeer)
        guard !parsedPeers.isEmpty else { throw AWGConfigParserError.missingValue("Peer") }
        return TunnelConfiguration(name: name, interface: interface, peers: parsedPeers)
    }

    private static func makeInterface(_ values: [String: String]) throws -> InterfaceConfiguration {
        let allowed: Set<String> = [
            "privatekey", "address", "dns", "listenport", "mtu",
            "jc", "jmin", "jmax", "s1", "s2", "s3", "s4",
            "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5",
            "headerprotectionkey", "contentpaddingaddition", "rekeyaftertime",
            "rekeytimeout", "rejectaftertime", "keepalivetimeout",
            "maxhandshakeattempts", "randomtrailers", "disablecookies",
        ]
        if let key = values.keys.first(where: { !allowed.contains($0) }) {
            throw AWGConfigParserError.unsupportedKey(key)
        }
        guard let rawPrivateKey = values["privatekey"] else { throw AWGConfigParserError.missingValue("PrivateKey") }
        guard let privateKey = PrivateKey(base64Key: rawPrivateKey) else {
            throw AWGConfigParserError.invalidValue("PrivateKey", "***")
        }

        var interface = InterfaceConfiguration(privateKey: privateKey)
        interface.addresses = try list(values["address"]).map {
            guard let value = IPAddressRange(from: $0) else { throw AWGConfigParserError.invalidValue("Address", $0) }
            return value
        }
        for value in list(values["dns"]) {
            if let server = DNSServer(from: value) { interface.dns.append(server) }
            else { interface.dnsSearch.append(value) }
        }
        interface.listenPort = try uint16(values["listenport"], key: "ListenPort")
        interface.mtu = try uint16(values["mtu"], key: "MTU")
        interface.junkPacketCount = try uint16(values["jc"], key: "Jc")
        interface.junkPacketMinSize = try uint16(values["jmin"], key: "Jmin")
        interface.junkPacketMaxSize = try uint16(values["jmax"], key: "Jmax")
        interface.initPacketJunkSize = try uint16(values["s1"], key: "S1")
        interface.responsePacketJunkSize = try uint16(values["s2"], key: "S2")
        interface.cookieReplyPacketJunkSize = try uint16(values["s3"], key: "S3")
        interface.transportPacketJunkSize = try uint16(values["s4"], key: "S4")
        interface.initPacketMagicHeader = nonEmpty(values["h1"])
        interface.responsePacketMagicHeader = nonEmpty(values["h2"])
        interface.underloadPacketMagicHeader = nonEmpty(values["h3"])
        interface.transportPacketMagicHeader = nonEmpty(values["h4"])
        interface.specialJunk1 = nonEmpty(values["i1"])
        interface.specialJunk2 = nonEmpty(values["i2"])
        interface.specialJunk3 = nonEmpty(values["i3"])
        interface.specialJunk4 = nonEmpty(values["i4"])
        interface.specialJunk5 = nonEmpty(values["i5"])

        if let value = nonEmpty(values["headerprotectionkey"]) {
            guard let key = PrivateKey(base64Key: value) else {
                throw AWGConfigParserError.invalidValue("HeaderProtectionKey", "***")
            }
            interface.headerProtectionKey = key
        }
        interface.contentPaddingAddition = nonEmpty(values["contentpaddingaddition"])
        interface.rekeyAfterTime = nonEmpty(values["rekeyaftertime"])
        interface.rekeyTimeout = nonEmpty(values["rekeytimeout"])
        interface.rejectAfterTime = nonEmpty(values["rejectaftertime"])
        interface.keepaliveTimeout = nonEmpty(values["keepalivetimeout"])
        interface.maxHandshakeAttempts = nonEmpty(values["maxhandshakeattempts"])
        interface.randomTrailers = nonEmpty(values["randomtrailers"])
        interface.disableCookies = nonEmpty(values["disablecookies"])
        return interface
    }

    private static func makePeer(_ values: [String: String]) throws -> PeerConfiguration {
        let allowed: Set<String> = ["publickey", "presharedkey", "allowedips", "endpoint", "persistentkeepalive"]
        if let key = values.keys.first(where: { !allowed.contains($0) }) {
            throw AWGConfigParserError.unsupportedKey(key)
        }
        guard let rawPublicKey = values["publickey"] else { throw AWGConfigParserError.missingValue("PublicKey") }
        guard let publicKey = PublicKey(base64Key: rawPublicKey) else {
            throw AWGConfigParserError.invalidValue("PublicKey", "***")
        }
        var peer = PeerConfiguration(publicKey: publicKey)
        if let rawKey = nonEmpty(values["presharedkey"]) {
            guard let key = PreSharedKey(base64Key: rawKey) else {
                throw AWGConfigParserError.invalidValue("PresharedKey", "***")
            }
            peer.preSharedKey = key
        }
        peer.allowedIPs = try list(values["allowedips"]).map {
            guard let value = IPAddressRange(from: $0) else { throw AWGConfigParserError.invalidValue("AllowedIPs", $0) }
            return value
        }
        if let rawEndpoint = nonEmpty(values["endpoint"]) {
            guard let endpoint = Endpoint(from: rawEndpoint) else {
                throw AWGConfigParserError.invalidValue("Endpoint", rawEndpoint)
            }
            peer.endpoint = endpoint
        }
        peer.persistentKeepAlive = nonEmpty(values["persistentkeepalive"])
        return peer
    }

    private static func list(_ value: String?) -> [String] {
        value?.split(separator: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty } ?? []
    }

    private static func nonEmpty(_ value: String?) -> String? {
        guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return nil }
        return value
    }

    private static func uint16(_ value: String?, key: String) throws -> UInt16? {
        guard let value = nonEmpty(value) else { return nil }
        guard let result = UInt16(value) else { throw AWGConfigParserError.invalidValue(key, value) }
        return result
    }
}
