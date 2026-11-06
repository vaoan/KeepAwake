// KeepAwakeBar: the menu-bar item, drawn natively so it is as narrow as Apple's own (Java's TrayIcon adds a fixed
// margin on each side). KeepAwake starts it and drives it through stdin, one tab-separated command per line:
//   icon <base64 png>     44×44 px, shown at 22×22 pt (2x)
//   tip <text>            tooltip
//   menu <gen> <n>        a menu with generation number gen, followed by n lines, each "item <tag> <enabled 0|1> <checked -1|0|1> <label>", "sep",
//                         "sub <label>" (opens a submenu: the lines up to its "endsub" are its items) or "endsub".
//                         Submenus nest. n counts every line, "sub" and "endsub" included.
//   notify <title> <body> a system notification (Notification Center, Do Not Disturb…)
// It writes to stdout: "click <gen> <tag>" when a menu item is chosen (gen is the generation of the menu that row
// belonged to when it was shown, so the app runs the action of the row the person saw), "shown <gen>" when it starts
// displaying a menu, "toggle" on a left click on the icon,
// "notify-auth <1|0>" when notifications become allowed or not (also later), and exits when stdin closes (the app quit or died).
// Left click toggles; right click (or control-click) opens the menu.
// Notifications need it to run from its bundle (KeepAwakeBar.app, built by native/build-bar.sh): that's what macOS
// shows ("KeepAwake") and asks permission for. A plain java process can't post them.
import AppKit
import UserNotifications

/** One writer for stdout: menu clicks and notification callbacks come from different threads. */
let output = DispatchQueue(label: "stdout")
func emit(_ line: String) {
    output.async {
        print(line)
        fflush(stdout)
    }
}

final class Notes: NSObject, UNUserNotificationCenterDelegate {
    private let center = UNUserNotificationCenter.current()
    /** Only touched on the output queue. */
    private var lastReported: Bool?

    func setUp() {
        center.delegate = self
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            self.report(granted)
            // The first answer can be "no" even when the person clicks Allow, and System Settings can change it at
            // any time: keep asking, so KeepAwake follows without a restart.
            DispatchQueue.main.async {
                Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { _ in self.check() }
            }
        }
    }

    private func check() {
        center.getNotificationSettings { settings in
            self.report(settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional)
        }
    }

    /** Tells KeepAwake only when the answer changes. */
    private func report(_ allowed: Bool) {
        output.async {
            if self.lastReported == allowed { return }
            self.lastReported = allowed
            print("notify-auth\t\(allowed ? 1 : 0)")
            fflush(stdout)
        }
    }

    func post(title: String, body: String) {
        let c = UNMutableNotificationContent()
        c.title = title
        c.body = body
        c.sound = .default
        center.add(UNNotificationRequest(identifier: UUID().uuidString, content: c, trigger: nil)) { error in
            if let error { emit("notify-error\t\(error.localizedDescription)") }
        }
    }

    /** Shown even if this helper counts as frontmost (it never is: it has no windows). */
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler done: @escaping (UNNotificationPresentationOptions) -> Void) {
        done([.banner, .list, .sound])
    }
}

/** A menu description parsed from the "menu" lines. */
indirect enum Node {
    case item(tag: Int, enabled: Bool, checked: Bool, label: String)
    case sep
    case sub(label: String, children: [Node])
}

/** Builds the tree from the lines after "menu"; an unbalanced "endsub" is ignored and open submenus are closed at the end. */
func parseNodes(_ lines: [[String]]) -> [Node] {
    var stack: [(label: String, items: [Node])] = [("", [])]
    for p in lines {
        switch p[0] {
        case "sep":
            stack[stack.count - 1].items.append(.sep)
        case "item" where p.count >= 5:
            stack[stack.count - 1].items.append(.item(tag: Int(p[1]) ?? -1, enabled: p[2] == "1", checked: p[3] == "1", label: p[4]))
        case "sub":
            stack.append((p.count > 1 ? p[1] : "", []))
        case "endsub":
            if stack.count > 1 {
                let done = stack.removeLast()
                stack[stack.count - 1].items.append(.sub(label: done.label, children: done.items))
            }
        default:
            break
        }
    }
    while stack.count > 1 {
        let done = stack.removeLast()
        stack[stack.count - 1].items.append(.sub(label: done.label, children: done.items))
    }
    return stack[0].items
}

final class Bar: NSObject, NSMenuDelegate {
    static let size: CGFloat = 22
    /** Variable length: macOS sizes the item to the image and centres it (a fixed length left it off-centre). */
    let item = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
    let menu = NSMenu()
    private var expected = 0
    private var incoming: [[String]] = []
    private var incomingGen = 0
    private var isOpen = false
    private var shownGen = 0
    /** A menu of a different shape that arrived while the menu was open: applied when it closes. */
    private var held: (gen: Int, nodes: [Node])?

    override init() {
        super.init()
        menu.autoenablesItems = false
        menu.delegate = self
        // item.menu stays nil: a menu on the item would open on the left click too. It is attached only for a right
        // click (see clicked) and removed in menuDidClose.
        if let button = item.button {
            button.imagePosition = .imageOnly
            button.target = self
            button.action = #selector(clicked(_:))
            button.sendAction(on: [.leftMouseUp, .rightMouseDown])
        }
    }

    @objc func clicked(_ sender: Any?) {
        guard let event = NSApp.currentEvent else { return }
        if event.type == .rightMouseDown || (event.type == .leftMouseUp && event.modifierFlags.contains(.control)) {
            guard !menu.items.isEmpty else { return }
            item.menu = menu
            item.button?.performClick(nil) // menuDidClose clears item.menu
        } else if event.type == .leftMouseUp {
            emit("toggle")
        }
    }

    func handle(_ line: String) {
        let parts = line.components(separatedBy: "\t")
        switch parts[0] {
        case "icon":
            guard parts.count > 1, let data = Data(base64Encoded: parts[1]), let image = NSImage(data: data) else { return }
            image.size = NSSize(width: Bar.size, height: Bar.size)
            item.button?.image = image
        case "tip":
            item.button?.toolTip = parts.count > 1 ? parts[1] : nil
        case "menu":
            incomingGen = parts.count > 1 ? Int(parts[1]) ?? 0 : 0
            expected = parts.count > 2 ? Int(parts[2]) ?? 0 : 0
            incoming = []
            if expected == 0 { apply() }
        case "item", "sep", "sub", "endsub":
            guard expected > 0 else { return }
            incoming.append(parts)
            if incoming.count == expected { apply() }
        case "notify":
            guard parts.count >= 3 else { return }
            notes.post(title: parts[1], body: parts[2])
        default:
            break
        }
    }

    private func apply() {
        let nodes = parseNodes(incoming)
        incoming = []
        expected = 0
        // While the menu is open only same-shape updates (in place); a different shape would move rows under the
        // pointer and remove the item that owns an open submenu, so the latest one waits for the close.
        if isOpen && !sameShape(menu, nodes) {
            held = (incomingGen, nodes)
            return
        }
        held = nil
        show(nodes, gen: incomingGen)
    }

    private func show(_ nodes: [Node], gen: Int) {
        update(menu, to: nodes, gen: gen)
        if gen != shownGen {
            shownGen = gen
            emit("shown\t\(gen)")
        }
    }

    /** Same shape (recursively) as the open menu: update in place (safe while it is showing); otherwise rebuild. */
    private func update(_ m: NSMenu, to nodes: [Node], gen: Int) {
        if !sameShape(m, nodes) {
            m.removeAllItems()
            for n in nodes {
                switch n {
                case .sep:
                    m.addItem(.separator())
                case .item:
                    m.addItem(NSMenuItem(title: "", action: #selector(pick(_:)), keyEquivalent: ""))
                case .sub:
                    let mi = NSMenuItem(title: "", action: nil, keyEquivalent: "")
                    let sub = NSMenu()
                    sub.autoenablesItems = false
                    mi.submenu = sub
                    m.addItem(mi)
                }
            }
        }
        for (mi, n) in zip(m.items, nodes) {
            switch n {
            case .sep:
                break
            case let .item(tag, enabled, checked, label):
                mi.target = self
                mi.tag = tag
                mi.representedObject = gen // the generation this row belongs to, read when it is chosen
                mi.isEnabled = enabled
                mi.state = checked ? .on : .off
                mi.title = label
            case let .sub(label, children):
                mi.title = label
                if let sub = mi.submenu { update(sub, to: children, gen: gen) }
            }
        }
    }

    private func sameShape(_ m: NSMenu, _ nodes: [Node]) -> Bool {
        guard m.items.count == nodes.count else { return false }
        return zip(m.items, nodes).allSatisfy { mi, n in
            switch n {
            case .sep: return mi.isSeparatorItem
            case .item: return !mi.isSeparatorItem && mi.submenu == nil
            case let .sub(_, children): return mi.submenu.map { sameShape($0, children) } ?? false
            }
        }
    }

    func menuWillOpen(_ menu: NSMenu) {
        if menu === self.menu { isOpen = true }
    }

    func menuDidClose(_ menu: NSMenu) {
        guard menu === self.menu else { return }
        isOpen = false
        item.menu = nil
        // Not now: the chosen item's action is sent after this, and must still find its row.
        RunLoop.main.perform(inModes: [.common]) {
            if !self.isOpen, let h = self.held {
                self.held = nil
                self.show(h.nodes, gen: h.gen)
            }
        }
    }

    @objc func pick(_ sender: NSMenuItem) {
        emit("click\t\((sender.representedObject as? Int) ?? shownGen)\t\(sender.tag)")
    }
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)
let notes = Notes()
// Only from the bundle: outside one, UNUserNotificationCenter aborts the process.
if Bundle.main.bundleIdentifier != nil { notes.setUp() } else { emit("notify-auth\t0") }
let bar = Bar()
Thread {
    while let line = readLine() {
        // not DispatchQueue.main: it waits while the menu is open; these modes also run during menu tracking
        RunLoop.main.perform(inModes: [.common, .eventTracking]) { bar.handle(line) }
        CFRunLoopWakeUp(CFRunLoopGetMain())
    }
    exit(0)
}.start()
app.run()
