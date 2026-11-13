import UIKit
import WebKit
import UserNotifications

// Focus for iPhone: the same page as the website and the Android app, inside a native shell that adds what Safari
// can't do: the end bell as a notification when the iPhone is locked, real vibration, the screen kept on during a
// sit, and daily backups in Files > On My iPhone > Focus.

private let SITE = URL(string: "https://abuelgasimmohamedsaeed.github.io/Focus/")!

@main
final class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        let w = UIWindow(frame: UIScreen.main.bounds)
        w.rootViewController = FocusViewController()
        w.makeKeyAndVisible()
        window = w
        return true
    }

    // Focus is open: the page plays its own bell, so the notification stays quiet.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([])
    }
}

final class FocusViewController: UIViewController, WKNavigationDelegate, WKScriptMessageHandler, WKScriptMessageHandlerWithReply {
    private var web: WKWebView!
    private var triedCache = false

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 9 / 255, green: 9 / 255, blue: 11 / 255, alpha: 1)

        let ucc = WKUserContentController()
        // the page talks to the iPhone through window.stillNative, the same way it talks to the laptop and Android apps
        let shim = """
        window.stillNative = {
          ios: true,
          sitState: s => window.webkit.messageHandlers.focus.postMessage({ kind: 'sit', value: s ? JSON.stringify(s) : 'null' }),
          saveBackup: json => window.webkit.messageHandlers.focus.postMessage({ kind: 'backup', value: json }),
          findBackup: async () => { const j = await window.webkit.messageHandlers.focusReply.postMessage({ kind: 'findBackup' }); return j ? { json: j } : null; },
          mini: async () => false
        };
        navigator.vibrate = p => { window.webkit.messageHandlers.focus.postMessage({ kind: 'vibrate', value: JSON.stringify(p) }); return true; };
        """
        ucc.addUserScript(WKUserScript(source: shim, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        ucc.add(self, name: "focus")
        ucc.addScriptMessageHandler(self, contentWorld: .page, name: "focusReply")

        let cfg = WKWebViewConfiguration()
        cfg.userContentController = ucc
        cfg.allowsInlineMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []
        cfg.websiteDataStore = .default()

        web = WKWebView(frame: view.bounds, configuration: cfg)
        web.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        web.isOpaque = false
        web.backgroundColor = view.backgroundColor
        web.scrollView.backgroundColor = view.backgroundColor
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.scrollView.bounces = false
        web.navigationDelegate = self
        view.addSubview(web)
        web.load(URLRequest(url: SITE, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 20))
    }

    // offline: show the last copy the iPhone kept, so the page and its saved progress still open
    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        guard !triedCache else { return }
        triedCache = true
        webView.load(URLRequest(url: SITE, cachePolicy: .returnCacheDataDontLoad, timeoutInterval: 20))
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        triedCache = false
    }

    // links to other sites (photo credits, GitHub) open in Safari
    func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if let url = action.request.url, action.navigationType == .linkActivated, url.host != SITE.host {
            UIApplication.shared.open(url)
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }

    func userContentController(_ ucc: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any], let kind = body["kind"] as? String else { return }
        let value = body["value"] as? String ?? ""
        switch kind {
        case "sit": Sit.update(value)
        case "vibrate": Haptics.play(value)
        case "backup": Backups.save(value)
        default: break
        }
    }

    func userContentController(_ ucc: WKUserContentController, didReceive message: WKScriptMessage,
                               replyHandler: @escaping (Any?, String?) -> Void) {
        replyHandler(Backups.newest(), nil)
    }
}

/// The running sit: the end bell is scheduled as a notification (it rings even when the iPhone is locked),
/// and the screen stays on while the sit runs.
enum Sit {
    private static let id = "focus.end"

    static func update(_ json: String) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [id])
        guard let data = json.data(using: .utf8),
              let s = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            DispatchQueue.main.async { UIApplication.shared.isIdleTimerDisabled = false }
            return
        }
        DispatchQueue.main.async { UIApplication.shared.isIdleTimerDisabled = true }
        let over = s["over"] as? Bool ?? false
        let endAt = (s["endAt"] as? Double ?? 0) / 1000
        let wait = endAt - Date().timeIntervalSince1970
        guard !over, wait > 1 else { return }
        center.requestAuthorization(options: [.alert, .sound]) { ok, _ in
            guard ok else { return }
            let c = UNMutableNotificationContent()
            let label = s["label"] as? String ?? ""
            c.title = label.isEmpty ? "Time's up" : label + " · time's up"
            let planned = Int(s["planned"] as? Double ?? 0)
            c.body = planned > 0 ? "\(planned) min done. Open Focus and tap Done." : "Open Focus and tap Done."
            let sound = s["sound"] as? String ?? "ring"
            if sound != "off" {
                let file = ["bowl", "hand", "ring", "temple", "tingsha"].contains(sound) ? "end_\(sound).caf" : "end_ring.caf"
                c.sound = UNNotificationSound(named: UNNotificationSoundName(file))
            }
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: wait, repeats: false)
            center.add(UNNotificationRequest(identifier: id, content: c, trigger: trigger))
        }
    }
}

/// navigator.vibrate patterns from the page, played with the iPhone's own haptics.
enum Haptics {
    static func play(_ json: String) {
        var parts: [Double] = []
        if let n = Double(json) { parts = [n] }
        else if let data = json.data(using: .utf8), let a = try? JSONSerialization.jsonObject(with: data) as? [Double] { parts = a }
        var at = 0.0
        for (i, ms) in parts.enumerated() {
            if i % 2 == 0, ms > 0 {
                let style: UIImpactFeedbackGenerator.FeedbackStyle = ms >= 250 ? .heavy : (ms >= 80 ? .medium : .light)
                DispatchQueue.main.asyncAfter(deadline: .now() + at) {
                    let g = UIImpactFeedbackGenerator(style: style)
                    g.impactOccurred(intensity: 1)
                    if ms >= 250 { DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) { g.impactOccurred(intensity: 1) } }
                }
            }
            at += ms / 1000
        }
    }
}

/// One backup file a day in Files > On My iPhone > Focus > Focus backups; the last 30 days are kept.
enum Backups {
    private static var folder: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let f = docs.appendingPathComponent("Focus backups", isDirectory: true)
        try? FileManager.default.createDirectory(at: f, withIntermediateDirectories: true)
        return f
    }

    static func save(_ json: String) {
        let day = ISO8601DateFormatter.string(from: Date(), timeZone: .current, formatOptions: [.withFullDate])
        try? json.write(to: folder.appendingPathComponent("focus-backup-\(day).json"), atomically: true, encoding: .utf8)
        let old = Date().addingTimeInterval(-30 * 86400)
        for f in files() {
            if let d = (try? f.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate, d < old {
                try? FileManager.default.removeItem(at: f)
            }
        }
    }

    static func newest() -> String? {
        guard let f = files().last else { return nil }
        return try? String(contentsOf: f, encoding: .utf8)
    }

    private static func files() -> [URL] {
        let all = (try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
        return all.filter { $0.lastPathComponent.hasPrefix("focus-backup-") }.sorted { $0.lastPathComponent < $1.lastPathComponent }
    }
}
