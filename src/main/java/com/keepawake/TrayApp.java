package com.keepawake;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * Menu bar item. Normally the native helper (NativeBar); if it is missing or dies, the AWT TrayIcon with a
 * JPopupMenu. Both render {@link MenuModel}. All Controller calls happen on the EDT.
 */
final class TrayApp {
    private static final int TICK_MILLIS = 3000;
    private static final int MAX_CUSTOM_MINUTES = 10080;
    private static final String INVALID_MINUTES = "Escribe un número entero de minutos mayor que 0";
    private static final String TOO_LARGE_MINUTES = "El máximo es 10080 minutos (una semana)";

    private final Controller controller;
    private final Caffeinator caffeinator = Caffeinator.system();
    private final ClickGate gate = new ClickGate();
    private final Timer timer;
    private TrayIcon trayIcon;
    private NativeBar bar;
    private boolean quitting;
    private boolean customTimerOpen;
    private JDialog anchor;

    TrayApp() {
        Path dir = Path.of(System.getProperty("user.home"), ".claude", "sessions");
        SessionScanner scanner = new SessionScanner(dir, SessionScanner.systemProbe());
        controller = new Controller(caffeinator, scanner::scan, this::refresh);
        timer = new Timer(TICK_MILLIS, e -> tickAndNotify());
    }

    private void tickAndNotify() {
        String errBefore = controller.lastError();
        controller.tick(Instant.now());
        refresh();
        if (controller.consumeAutoOffNotice()) {
            notifyUser("Claude terminó. El Mac vuelve a su comportamiento normal.");
        }
        String err = controller.lastError();
        if (err != null && !err.equals(errBefore)) {
            notifyUser(err);
        }
    }

    /**
     * The helper's notification when it is alive and macOS allows it (or has not said yet); otherwise a script
     * notification. (AWT's displayMessage is dropped by macOS for a plain java process.)
     */
    private void notifyUser(String message) {
        if (bar != null && bar.isAlive() && !Boolean.FALSE.equals(bar.notificationsAllowed())) {
            bar.notify("KeepAwake", message);
        } else {
            ScriptNotification.post("KeepAwake", message);
        }
    }

    void start() throws AWTException {
        String helper = System.getProperty("keepawake.bar");
        if (helper != null) {
            bar = NativeBar.start(Path.of(helper), this::toggle, this::refresh,
                    () -> EventQueue.invokeLater(this::helperDied));
        }
        if (bar == null) {
            addAwtTray();
        }
        refresh();
        timer.start();
    }

    /** The helper exited by itself: bring back the AWT TrayIcon. */
    private void helperDied() {
        if (quitting || bar == null) {
            return;
        }
        bar = null;
        try {
            addAwtTray();
        } catch (AWTException | UnsupportedOperationException e) {
            e.printStackTrace();
            System.exit(1);
        }
        refresh();
    }

    private void addAwtTray() throws AWTException {
        if (!SystemTray.isSupported()) {
            throw new UnsupportedOperationException("Esta plataforma no admite icono en la barra de menús");
        }
        trayIcon = new TrayIcon(Icons.off(), "KeepAwake");
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                act(gate.pressed(e.isPopupTrigger()), e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                act(gate.released(e.isPopupTrigger(), e.getButton()), e);
            }
        });
        SystemTray.getSystemTray().add(trayIcon);
    }

    /** Safe from any thread (JVM shutdown hook): only stops the synchronized Caffeinator, not the Controller. */
    void shutdown() {
        caffeinator.stop();
    }

    private void toggle() {
        controller.toggle(Instant.now());
        refresh();
    }

    private void act(ClickGate.Action action, MouseEvent e) {
        switch (action) {
            case SHOW_MENU -> showMenu(e);
            case TOGGLE -> toggle();
            case NONE -> { }
        }
    }

    private void refresh() {
        Instant now = Instant.now();
        Snapshot snap = controller.snapshot();
        String tip = tooltip(MenuModel.statusLine(controller, now),
                MenuModel.count(snap, SessionStatus.WORKING, SessionStatus.BACKGROUND),
                MenuModel.count(snap, SessionStatus.WAITING), MenuModel.count(snap, SessionStatus.UNKNOWN),
                controller.lastError());
        boolean warn = controller.hasWarning();
        if (bar != null) {
            bar.setIcon(controller.isOn() ? (warn ? Icons.warning2x() : Icons.on2x()) : Icons.off2x());
            bar.setTooltip(tip);
            bar.syncMenu(model(now));
        } else if (trayIcon != null) {
            trayIcon.setImage(controller.isOn() ? (warn ? Icons.warning() : Icons.on()) : Icons.off());
            trayIcon.setToolTip(tip);
        }
    }

    private List<MenuModel.Entry> model(Instant now) {
        return MenuModel.build(controller, now, this::customTimer, this::quit);
    }

    private void quit() {
        quitting = true;
        controller.shutdown();
        if (bar != null) {
            bar.close();
        }
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        System.exit(0);
    }

    /** Status line plus session counts (when any session exists) plus the last error (when present). */
    static String tooltip(String statusLine, long working, long waiting, long unknown, String error) {
        StringBuilder sb = new StringBuilder(statusLine);
        if (working + waiting + unknown > 0) {
            sb.append(" · ").append(working).append(" trabajando, ").append(waiting).append(" en espera");
            if (unknown > 0) sb.append(", ").append(unknown).append(" desconocidas");
        }
        if (error != null) sb.append(" · ").append(error);
        return sb.toString();
    }

    // ---- menu ----

    private void showMenu(MouseEvent e) {
        hideAnchor();
        tickAndNotify();
        JPopupMenu menu = buildMenu();
        Dimension size = menu.getPreferredSize();
        Point p = new Point(e.getXOnScreen(), e.getYOnScreen());
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration();
        for (var dev : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            if (dev.getDefaultConfiguration().getBounds().contains(p)) {
                gc = dev.getDefaultConfiguration();
            }
        }
        Rectangle b = gc.getBounds();
        Insets in = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int minX = b.x + in.left;
        int maxX = b.x + b.width - in.right - size.width;
        int minY = b.y + in.top;
        int maxY = b.y + b.height - in.bottom - size.height;
        int x = Math.max(minX, Math.min(p.x, maxX));
        int y = Math.max(minY, Math.min(p.y, maxY));

        JDialog d = new JDialog();
        d.setUndecorated(true);
        d.setAlwaysOnTop(true);
        d.setSize(1, 1);
        d.setLocation(x, y);
        d.setVisible(true);
        anchor = d;
        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent ev) {}

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent ev) {
                SwingUtilities.invokeLater(() -> {
                    if (anchor == d) {
                        hideAnchor();
                    }
                });
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent ev) {}
        });
        menu.show(d, 0, 0);
    }

    private void hideAnchor() {
        if (anchor != null) {
            anchor.setVisible(false);
            anchor.dispose();
            anchor = null;
        }
    }

    private JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();
        addAll(menu, model(Instant.now()));
        return menu;
    }

    private void addAll(JComponent parent, List<MenuModel.Entry> entries) {
        for (MenuModel.Entry e : entries) {
            if (e.isSeparator()) {
                if (parent instanceof JPopupMenu p) {
                    p.addSeparator();
                } else {
                    ((JMenu) parent).addSeparator();
                }
                continue;
            }
            JMenuItem i;
            if (e.isSubmenu()) {
                JMenu sub = new JMenu(e.label());
                addAll(sub, e.children());
                i = sub;
            } else if (e.checked() != null) {
                i = new JCheckBoxMenuItem(e.label(), e.checked());
            } else {
                i = new JMenuItem(e.label());
            }
            i.setEnabled(e.enabled());
            if (e.action() != null) {
                i.addActionListener(ev -> {
                    e.action().run();
                    refresh();
                });
            }
            parent.add(i);
        }
    }

    private void customTimer() {
        if (customTimerOpen) {
            return; // a second click while the dialog's nested event loop runs must not stack another
        }
        customTimerOpen = true;
        try {
            askCustomTimer();
        } finally {
            customTimerOpen = false;
        }
    }

    private void askCustomTimer() {
        JOptionPane pane = new JOptionPane("Minutos:", JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        pane.setWantsInput(true);
        pane.setInitialSelectionValue("60");
        showInFront(pane, "Apagar tras");
        Object input = pane.getInputValue();
        if (!Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue()) || input == null
                || input == JOptionPane.UNINITIALIZED_VALUE) {
            return;
        }
        long minutes;
        try {
            minutes = Long.parseLong(input.toString().trim());
        } catch (NumberFormatException ex) {
            message(INVALID_MINUTES);
            return;
        }
        if (minutes <= 0) {
            message(INVALID_MINUTES);
        } else if (minutes > MAX_CUSTOM_MINUTES) {
            message(TOO_LARGE_MINUTES);
        } else {
            MenuModel.setTimer(controller, minutes);
        }
    }

    private void message(String text) {
        showInFront(new JOptionPane(text, JOptionPane.INFORMATION_MESSAGE), "KeepAwake");
    }

    /**
     * Shows the pane in a modal, always-on-top dialog and asks macOS to bring this process to the front: when the
     * native menu fires, another app is frontmost and a plain dialog would open behind it, unable to take typing.
     */
    private static void showInFront(JOptionPane pane, String title) {
        JDialog dialog = pane.createDialog(null, title);
        dialog.setAlwaysOnTop(true);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                requestForeground();
                dialog.toFront();
            }
        });
        requestForeground();
        dialog.setVisible(true);
        dialog.dispose();
    }

    private static void requestForeground() {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                desktop.requestForeground(true);
            }
        }
    }
}
