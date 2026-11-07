import SwiftUI

@main
struct InoagentVPNApp: App {
    @StateObject private var store: ProfileStore
    @StateObject private var account: AppleAccountModel
    init() {
        let profiles = ProfileStore()
        _store = StateObject(wrappedValue: profiles)
        _account = StateObject(wrappedValue: AppleAccountModel(profiles: profiles))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(account)
                .preferredColorScheme(.dark)
        }
    }
}
