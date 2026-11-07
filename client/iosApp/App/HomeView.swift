import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: ProfileStore

    var body: some View {
        ZStack {
            AuroraBackground()
            ScrollView {
                VStack(spacing: 22) {
                    header
                    ConnectionOrb(
                        phase: store.vpn.phase,
                        hasProfile: store.selectedProfile != nil,
                        action: store.toggleConnection
                    )
                    status
                    ProfilePickerCard()
                    TrafficCard(traffic: store.vpn.traffic)
                }
                .padding(.horizontal, 20)
                .padding(.top, 18)
                .padding(.bottom, 28)
            }
            .scrollIndicators(.hidden)
        }
        .task(id: store.vpn.phase) {
            guard store.vpn.phase == .connected else { return }
            while !Task.isCancelled {
                await store.vpn.refreshTraffic()
                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 3) {
                Text("Inoagent VPN")
                    .font(.title2.weight(.semibold))
                Text("Ваше пространство свободы")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "crown.fill")
                .font(.body.weight(.semibold))
                .foregroundStyle(Color("BrandLavender"))
                .frame(width: 42, height: 42)
                .background(.ultraThinMaterial, in: Circle())
                .accessibilityLabel("Inoagent VPN")
        }
    }

    private var status: some View {
        VStack(spacing: 6) {
            HStack(spacing: 8) {
                Circle()
                    .fill(statusColor)
                    .frame(width: 8, height: 8)
                Text(store.vpn.phase.title)
                    .font(.headline)
            }

            if case .failed(let message) = store.vpn.phase {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            } else if store.vpn.phase == .connected, let date = store.vpn.connectedAt {
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    Text(Self.duration(from: date, to: context.date))
                        .font(.system(.body, design: .rounded).monospacedDigit())
                        .foregroundStyle(.secondary)
                }
            } else {
                Text(store.selectedProfile == nil ? "Добавьте конфигурацию, чтобы начать" : "Быстро. Безопасно. Без границ.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
        .animation(.easeInOut(duration: 0.25), value: store.vpn.phase)
    }

    private var statusColor: Color {
        switch store.vpn.phase {
        case .connected: return Color("BrandMint")
        case .connecting, .disconnecting: return Color("BrandLavender")
        case .disconnected: return .secondary
        case .failed: return .red
        }
    }

    private static func duration(from start: Date, to end: Date) -> String {
        let seconds = max(0, Int(end.timeIntervalSince(start)))
        return String(format: "%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60)
    }
}

private struct ConnectionOrb: View {
    let phase: ConnectionPhase
    let hasProfile: Bool
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false
    @State private var spin = false

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(Color("BrandMint").opacity(phase == .connected ? 0.24 : 0.10))
                    .frame(width: 220, height: 220)
                    .blur(radius: 24)
                    .scaleEffect(pulse ? 1.08 : 0.92)

                Circle()
                    .stroke(
                        AngularGradient(
                            colors: [Color("BrandMint"), .white.opacity(0.8), Color("BrandLavender"), Color("BrandMint")],
                            center: .center
                        ),
                        lineWidth: 3
                    )
                    .rotationEffect(.degrees(spin ? 360 : 0))
                    .shadow(color: Color("BrandMint").opacity(0.65), radius: 18)

                Circle()
                    .fill(.ultraThinMaterial)
                    .padding(10)

                VStack(spacing: 12) {
                    Image(systemName: symbol)
                        .font(.system(size: 30, weight: .light))
                        .symbolEffect(.pulse, isActive: phase.isBusy)
                    Text(buttonTitle)
                        .font(.headline)
                }
                .foregroundStyle(.white)
            }
            .frame(width: 230, height: 230)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .disabled(phase.isBusy)
        .opacity(hasProfile ? 1 : 0.72)
        .accessibilityLabel(buttonTitle)
        .accessibilityHint(hasProfile ? "Изменяет состояние VPN-соединения" : "Сначала добавьте конфигурацию")
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 2.4).repeatForever(autoreverses: true)) { pulse = true }
            withAnimation(.linear(duration: 10).repeatForever(autoreverses: false)) { spin = true }
        }
    }

    private var symbol: String {
        switch phase {
        case .connected: return "checkmark.shield.fill"
        case .connecting, .disconnecting: return "arrow.triangle.2.circlepath"
        case .disconnected, .failed: return "power"
        }
    }

    private var buttonTitle: String {
        switch phase {
        case .connected: return "Отключить"
        case .connecting: return "Подключение"
        case .disconnecting: return "Отключение"
        case .disconnected, .failed: return "Подключиться"
        }
    }
}

private struct ProfilePickerCard: View {
    @EnvironmentObject private var store: ProfileStore

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: "bolt.fill")
                .font(.title3)
                .foregroundStyle(.black)
                .frame(width: 42, height: 42)
                .background(Color("BrandMint"), in: RoundedRectangle(cornerRadius: 13, style: .continuous))

            VStack(alignment: .leading, spacing: 3) {
                Text(store.selectedProfile?.name ?? "Нет конфигурации")
                    .font(.headline)
                    .lineLimit(1)
                Text(store.selectedProfile?.endpoint ?? "Импортируйте файл AmneziaWG")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()

            if !store.profiles.isEmpty {
                Menu {
                    ForEach(store.profiles) { profile in
                        Button {
                            store.select(profile)
                        } label: {
                            if profile.id == store.selectedProfile?.id {
                                Label(profile.name, systemImage: "checkmark")
                            } else {
                                Text(profile.name)
                            }
                        }
                    }
                } label: {
                    Image(systemName: "chevron.up.chevron.down")
                        .foregroundStyle(.secondary)
                        .frame(width: 36, height: 36)
                }
                .accessibilityLabel("Выбрать конфигурацию")
            }
        }
        .glassCard()
    }
}

private struct TrafficCard: View {
    let traffic: TrafficSnapshot

    var body: some View {
        HStack(spacing: 0) {
            metric(title: "Получено", value: traffic.receivedBytes, symbol: "arrow.down")
            Divider().frame(height: 48)
            metric(title: "Отправлено", value: traffic.sentBytes, symbol: "arrow.up")
        }
        .glassCard()
    }

    private func metric(title: String, value: UInt64, symbol: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: symbol)
                .foregroundStyle(Color("BrandMint"))
            VStack(alignment: .leading, spacing: 3) {
                Text(ByteCountFormatter.string(fromByteCount: Int64(value), countStyle: .binary))
                    .font(.headline.monospacedDigit())
                Text(title)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 4)
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 10)
        .accessibilityElement(children: .combine)
    }
}

private struct AuroraBackground: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drift = false

    var body: some View {
        ZStack {
            Color(red: 0.025, green: 0.035, blue: 0.045)
            Ellipse()
                .fill(Color("BrandLavender").opacity(0.32))
                .frame(width: 310, height: 440)
                .blur(radius: 80)
                .offset(x: drift ? 150 : 90, y: drift ? -250 : -170)
            Ellipse()
                .fill(Color("BrandMint").opacity(0.25))
                .frame(width: 280, height: 360)
                .blur(radius: 74)
                .offset(x: drift ? -130 : -80, y: drift ? 310 : 220)
        }
        .ignoresSafeArea()
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 7).repeatForever(autoreverses: true)) { drift = true }
        }
    }
}

extension View {
    fileprivate func glassCard() -> some View {
        padding(16)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .stroke(.white.opacity(0.10), lineWidth: 1)
            }
    }
}
