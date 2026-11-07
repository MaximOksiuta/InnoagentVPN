import SwiftUI

struct RootView: View {
    @EnvironmentObject private var store: ProfileStore

    var body: some View {
        AppleClientView()
        .alert(
            "Inoagent VPN",
            isPresented: Binding(
                get: { store.alertText != nil },
                set: { if !$0 { store.dismissAlert() } }
            )
        ) {
            Button("Понятно", role: .cancel) { store.dismissAlert() }
        } message: {
            Text(store.alertText ?? "Неизвестная ошибка")
        }
    }
}
