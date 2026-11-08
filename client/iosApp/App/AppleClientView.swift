import SwiftUI
import UniformTypeIdentifiers
import WebKit
import Shared

private let graphite = Color(red: 20/255, green: 20/255, blue: 21/255)
private let panelColor = Color(red: 29/255, green: 29/255, blue: 32/255)
private let copper = Color(red: 229/255, green: 170/255, blue: 134/255)

struct AppleClientView: View {
    @EnvironmentObject private var account: AppleAccountModel
    @EnvironmentObject private var profiles: ProfileStore
    @State private var importing = false
    var body: some View {
        TabView {
            page {
                Text("Ваш маршрут").font(.largeTitle.weight(.medium))
                Text("Один аккаунт. Все ваши устройства.").foregroundStyle(.secondary)
                panel {
                    RouteDrawing(busy: account.busy || profiles.vpn.phase.isBusy, connected: profiles.vpn.phase == .connected).frame(height: 150)
                    #if COMPANION
                    Text(account.busy ? account.activity : "Конфигурация для AmneziaWG").font(.title3)
                    Text("Выберите сервер и передайте файл установленному VPN-клиенту").font(.subheadline).foregroundStyle(.secondary)
                    #else
                    Text(account.busy ? account.activity : profiles.vpn.phase.title).font(.title3)
                    if profiles.vpn.phase == .connected { Text("Получено \(profiles.vpn.traffic.receivedBytes / 1024) КБ · Отправлено \(profiles.vpn.traffic.sentBytes / 1024) КБ").font(.caption).foregroundStyle(.secondary) }
                    #endif
                    Button { Task { await account.connect() } } label: {
                        Text(connectTitle).frame(maxWidth: .infinity).padding(.vertical, 8)
                    }.buttonStyle(.borderedProminent).disabled(account.busy || profiles.vpn.phase.isBusy || account.user == nil)
                    #if COMPANION
                    if let url = account.exportURL { ShareLink("Передать в AmneziaWG", item: url).buttonStyle(.bordered) }
                    #endif
                }
                HStack { Text("Серверы").font(.title2); Spacer(); Button("Обновить") { Task { await account.refreshConfiguration() } }.disabled(account.user == nil || account.busy) }
                ForEach(account.servers, id: \.id) { server in
                    Button { account.selectedServerID = server.id } label: {
                        HStack(spacing: 12) {
                            ServerPostcard(url: account.artwork(server)).frame(width: 64, height: 48).clipShape(RoundedRectangle(cornerRadius: 10))
                            VStack(alignment: .leading, spacing: 4) {
                                Text(server.name).foregroundStyle(.primary); Text(server.location).font(.caption).foregroundStyle(.secondary)
                                if let status = account.statuses.first(where: { $0.id == server.id }) { Text(status.hostReachable ? "С хоста: доступен" : "С хоста: нет связи").font(.caption2).foregroundStyle(.secondary) }
                            }
                            Spacer()
                            if account.selectedServerID == server.id { Image(systemName: "checkmark").foregroundStyle(copper) }
                        }.padding(14).background(panelColor, in: RoundedRectangle(cornerRadius: 12)).overlay(RoundedRectangle(cornerRadius: 12).stroke(account.selectedServerID == server.id ? copper : .white.opacity(0.12)))
                    }.buttonStyle(.plain).disabled(account.busy || profiles.vpn.phase.isBusy || profiles.vpn.phase == .connected)
                }
                ForEach(account.notifications.filter { !$0.read }.prefix(5), id: \.id) { row in
                    panel {
                        HStack {
                            ServerPostcard(url: URL(string: account.api.artworkUrl(serverId: row.serverId, name: row.serverName))).frame(width: 56, height: 42).clipShape(RoundedRectangle(cornerRadius: 8))
                            Text("Новый сервер: \(row.serverName)").font(.headline)
                        }
                        Text(row.location).foregroundStyle(.secondary)
                        Button("Выбрать сервер") { account.selectedServerID = row.serverId; Task { await account.markRead(row) } }.disabled(account.busy || profiles.vpn.phase != .disconnected)
                    }
                }
            }.tabItem { Label("Соединение", systemImage: "point.3.connected.trianglepath.dotted") }
            page {
                Text("Свои конфигурации").font(.largeTitle)
                Button("Импортировать .conf") { importing = true }.buttonStyle(.bordered)
                ForEach(profiles.visibleProfiles.filter { $0.serviceUserID == nil }) { profile in
                    panel {
                        Text(profile.name).font(.headline); Text(profile.endpoint).font(.caption).foregroundStyle(.secondary)
                        #if COMPANION
                        Text("Для подключения используйте AmneziaWG").foregroundStyle(.secondary)
                        #else
                        Button(profiles.vpn.phase == .connected && profiles.selectedProfileID == profile.id ? "Отключить" : "Подключить") { profiles.select(profile); profiles.toggleConnection() }.disabled(profiles.vpn.phase.isBusy)
                        #endif
                        Button("Удалить локальный профиль", role: .destructive) { profiles.delete(profile) }.disabled(profiles.vpn.phase != .disconnected)
                    }
                }
            }.tabItem { Label("Импорт", systemImage: "doc") }
            page {
                if let user = account.user {
                    Text(user.nickname).font(.largeTitle); Text(user.phone).foregroundStyle(.secondary)
                    Text("При первом подключении это устройство появится в вашем кабинете на сайте.").foregroundStyle(.secondary)
                    Button("Выйти из аккаунта", role: .destructive, action: account.logout).disabled(account.busy)
                } else { AppleLoginForm() }
            }.tabItem { Label("Аккаунт", systemImage: "person.crop.circle") }
        }
        .tint(copper).preferredColorScheme(.dark)
        .task(id: profiles.vpn.phase) {
            guard profiles.vpn.phase == .connected else { return }
            while !Task.isCancelled { await profiles.vpn.refreshTraffic(); try? await Task.sleep(for: .seconds(1)) }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [UTType(filenameExtension: "conf") ?? .plainText, .plainText], allowsMultipleSelection: false) { result in
            switch result { case .success(let urls): if let url = urls.first { profiles.importConfiguration(from: url) }; case .failure(let error): profiles.alertText = error.localizedDescription }
        }
    }
    private var connectTitle: String {
        if account.busy { return "Подождите…" }
        #if COMPANION
        return "Подготовить конфигурацию"
        #else
        return profiles.vpn.phase == .connected ? "Отключить" : "Подключиться"
        #endif
    }
    private func page<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack { RouteBrand().frame(width: 36, height: 36); Text("inoagent").font(.title2.weight(.semibold)); Text("vpn").font(.caption).foregroundStyle(.secondary); Spacer(); Link("Сайт", destination: URL(string: "https://vpn.oksiuta.ru")!) }
                Divider()
                if let message = account.message { Text(message).font(.subheadline).padding(14).frame(maxWidth: .infinity, alignment: .leading).background(panelColor, in: RoundedRectangle(cornerRadius: 10)); Button("Закрыть") { account.message = nil } }
                content()
            }.padding(24).frame(maxWidth: 760, alignment: .leading).frame(maxWidth: .infinity)
        }.background(graphite)
    }
    private func panel<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 16, content: content).padding(20).frame(maxWidth: .infinity, alignment: .leading).background(panelColor, in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(.white.opacity(0.1)))
    }
}

private struct AppleLoginForm: View {
    @EnvironmentObject private var account: AppleAccountModel
    @State private var phone = ""
    @State private var password = ""
    @State private var nickname = ""
    @State private var registering = false
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(registering ? "Создать аккаунт" : "С возвращением").font(.largeTitle)
            Text("Ваш интернет. Ваш маршрут.").foregroundStyle(.secondary)
            TextField("Номер телефона", text: $phone).textContentType(.telephoneNumber)
            if registering { TextField("Имя", text: $nickname) }
            SecureField("Пароль", text: $password).textContentType(registering ? .newPassword : .password)
            Button(registering ? "Зарегистрироваться" : "Войти") {
                let value = password; password = ""
                Task { if registering { await account.register(phone: phone, password: value, nickname: nickname) } else { await account.login(phone: phone, password: value) } }
            }.buttonStyle(.borderedProminent).disabled(account.busy || phone.isEmpty || password.isEmpty || (registering && nickname.isEmpty))
            if account.busy { RouteDrawing(busy: true, connected: false).frame(width: 90, height: 56); Text(account.activity).foregroundStyle(.secondary) }
            Button(registering ? "Уже есть аккаунт" : "Создать аккаунт") { registering.toggle() }.disabled(account.busy)
            Link("Восстановить пароль через Telegram", destination: URL(string: "https://t.me/inoagentvpnbot?start=reset")!)
        }.textFieldStyle(.roundedBorder)
    }
}

private struct RouteBrand: View {
    var body: some View {
        Canvas { context, size in
            let s = size.width / 48
            var a = Path(); a.move(to: CGPoint(x: 8, y: 34)); a.addLine(to: CGPoint(x: 8, y: 18)); a.addQuadCurve(to: CGPoint(x: 18, y: 8), control: CGPoint(x: 8, y: 8)); a.addLines([CGPoint(x: 30, y: 8), CGPoint(x: 30, y: 18), CGPoint(x: 18, y: 18), CGPoint(x: 18, y: 34)]); a.closeSubpath()
            var b = Path(); b.move(to: CGPoint(x: 40, y: 14)); b.addLine(to: CGPoint(x: 40, y: 30)); b.addQuadCurve(to: CGPoint(x: 30, y: 40), control: CGPoint(x: 40, y: 40)); b.addLines([CGPoint(x: 18, y: 40), CGPoint(x: 18, y: 30), CGPoint(x: 30, y: 30), CGPoint(x: 30, y: 14)]); b.closeSubpath()
            context.scaleBy(x: s, y: s); context.fill(a, with: .color(copper)); context.fill(b, with: .color(copper)); context.fill(Path(ellipseIn: CGRect(x: 22, y: 22, width: 4, height: 4)), with: .color(.white))
        }.accessibilityHidden(true)
    }
}
private struct RouteDrawing: View {
    let busy: Bool
    let connected: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        TimelineView(.animation(minimumInterval: 1/30, paused: !busy || reduceMotion)) { timeline in
            Canvas { context, size in
                context.scaleBy(x: size.width/100, y: size.height/64)
                var p = Path(); p.move(to: CGPoint(x: 12, y: 32)); p.addLine(to: CGPoint(x: 26, y: 32)); p.addQuadCurve(to: CGPoint(x: 36, y: 22), control: CGPoint(x: 36, y: 32)); p.addLine(to: CGPoint(x: 36, y: 16)); p.addQuadCurve(to: CGPoint(x: 44, y: 8), control: CGPoint(x: 36, y: 8)); p.addLine(to: CGPoint(x: 56, y: 8)); p.addQuadCurve(to: CGPoint(x: 64, y: 16), control: CGPoint(x: 64, y: 8)); p.addLine(to: CGPoint(x: 64, y: 48)); p.addQuadCurve(to: CGPoint(x: 56, y: 56), control: CGPoint(x: 64, y: 56)); p.addLine(to: CGPoint(x: 44, y: 56)); p.addQuadCurve(to: CGPoint(x: 36, y: 48), control: CGPoint(x: 36, y: 56)); p.addLine(to: CGPoint(x: 36, y: 42)); p.addQuadCurve(to: CGPoint(x: 46, y: 32), control: CGPoint(x: 36, y: 32)); p.addLine(to: CGPoint(x: 88, y: 32))
                let color = connected ? Color(red: 0.44, green: 0.74, blue: 0.69) : copper
                let phase = busy && !reduceMotion ? timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1.6)/1.6 : 0.6
                context.stroke(p, with: .color(color.opacity(0.2)), lineWidth: 1.4)
                context.stroke(connected && !busy ? p : p.trimmedPath(from: max(0, phase-0.18), to: phase), with: .color(color), style: StrokeStyle(lineWidth: 1.8, lineCap: .round))
                for x in [12.0, 88.0] { context.fill(Path(ellipseIn: CGRect(x: x-2.5, y: 29.5, width: 5, height: 5)), with: .color(color)) }
            }
        }.accessibilityHidden(true)
    }
}

#if os(iOS)
private struct ServerPostcard: UIViewRepresentable {
    let url: URL?
    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration(); configuration.defaultWebpagePreferences.allowsContentJavaScript = false
        let view = WKWebView(frame: .zero, configuration: configuration); view.isOpaque = false; view.backgroundColor = .clear; view.scrollView.isScrollEnabled = false; view.isUserInteractionEnabled = false; return view
    }
    func updateUIView(_ view: WKWebView, context: Context) { if let url = url, view.url != url { view.load(URLRequest(url: url)) } }
}
#else
private struct ServerPostcard: NSViewRepresentable {
    let url: URL?
    func makeNSView(context: Context) -> WKWebView { let configuration = WKWebViewConfiguration(); configuration.defaultWebpagePreferences.allowsContentJavaScript = false; return WKWebView(frame: .zero, configuration: configuration) }
    func updateNSView(_ view: WKWebView, context: Context) { if let url = url, view.url != url { view.load(URLRequest(url: url)) } }
}
#endif
