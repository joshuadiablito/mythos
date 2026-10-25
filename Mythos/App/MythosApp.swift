import SwiftUI
import UserNotifications

/// Where the app should go next: set when a notification is tapped.
@Observable
final class Router {
    static let shared = Router()

    var selectedTab: AppTab = .today
    /// A story to open, from a tapped notification.
    var pendingStoryID: String?
}

enum AppTab: Hashable {
    case today, library, words, settings
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        #if DEBUG
        // `-openStory fr-a1-04` opens a story at launch, as a tapped notification would.
        if let id = UserDefaults.standard.string(forKey: "openStory") { Router.shared.pendingStoryID = id }
        #endif
        return true
    }

    // The completion-handler forms, not the async ones: iOS must be told on the
    // main thread that a tap was handled, or UIKit aborts ("Call must be made on
    // main thread") when it snapshots the scene.

    // Show the day's story even when Mythos is open.
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                            withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                            withCompletionHandler completionHandler: @escaping () -> Void) {
        let id = response.notification.request.content.userInfo[NotificationScheduler.storyIDKey] as? String
        DispatchQueue.main.async {
            if let id {
                Router.shared.selectedTab = .today
                Router.shared.pendingStoryID = id
            }
            completionHandler()
        }
    }
}

@main
struct MythosApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var settings = AppSettings()
    @State private var progress = ProgressStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(settings)
                .environment(progress)
                .environment(Router.shared)
                .environment(Speaker.shared)
                .tint(Theme.accent)
        }
        .onChange(of: scenePhase) { _, phase in
            // Top up the queue of daily notifications whenever the app comes back.
            if phase == .active, settings.hasOnboarded {
                Task { await NotificationScheduler.reschedule(settings: settings) }
            }
        }
    }
}
