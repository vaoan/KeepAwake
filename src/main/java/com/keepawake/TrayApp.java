package com.keepawake;

import java.awt.AWTException;
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
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/** Tray icon and menu. All Controller calls happen on the EDT. */
final class TrayApp {
    private static final int TICK_MILLIS = 3000;
    private static final int MAX_CUSTOM_MINUTES = 10080;
    private static final String INVALID_MINUTES = "Escribe un número entero de minutos mayor que 0";
    private static final String TOO_LARGE_MINUTES = "El máximo es 10080 minutos (una semana)";

    private final Controller controller;
    private final TrayIcon trayIcon;
    private final Timer timer;
    private JDialog anchor;

    TrayApp() {
        Path dir = Path.of(System.getProperty("user.home"), ".claude", "sessions");
        SessionScanner scanner = new SessionScanner(dir, SessionScanner.systemProbe());
        controller = new Controller(Caffeinator.system(), scanner::scan, this::refresh);
        trayIcon = new TrayIcon(Icons.off(), "KeepAwake");
        trayIcon.setImageAutoSize(true);
        timer = new Timer(TICK_MILLIS, e -> {
            controller.tick(Instant.now());
            refresh();
        });
    }

    void start() throws AWTException {
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handle(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handle(e);
            }
        });
        SystemTray.getSystemTray().add(trayIcon);
        refresh();
        timer.start();
    }

    void shutdown() {
        controller.shutdown();
    }

    private void handle(MouseEvent e) {
        if (e.isPopupTrigger()) {
            showMenu(e.getPoint(), e);
        } else if (e.getID() == MouseEvent.MOUSE_RELEASED && e.getButton() == MouseEvent.BUTTON1) {
            controller.toggle(Instant.now());
            refresh();
        }
    }

    private void refresh() {
        Instant now = Instant.now();
        trayIcon.setImage(controller.isOn()
                ? (controller.hasWarning() ? Icons.warning() : Icons.on())
                : Icons.off());
        trayIcon.setToolTip(statusLine(now));
    }

    private String statusLine(Instant now) {
        if (!controller.isOn()) {
            return "Inactivo";
        }
        Optional<Duration> remaining = soonest(now);
        return remaining.map(d -> "Activo · apaga en " + formatDuration(d)).orElse("Activo");
    }

    private Optional<Duration> soonest(Instant now) {
        Optional<Duration> fromDeadline = controller.deadline().map(d -> Duration.between(now, d));
        Optional<Duration> fromAuto = controller.autoOffRemaining(now);
        Optional<Duration> best = fromDeadline;
        if (fromAuto.isPresent() && (best.isEmpty() || fromAuto.get().compareTo(best.get()) < 0)) {
            best = fromAuto;
        }
        return best.map(d -> d.isNegative() ? Duration.ZERO : d);
    }

    /** "1 h 20 min", "5 min", "45 s". Rounds up to the next whole minute from one minute on. */
    static String formatDuration(Duration d) {
        long seconds = Math.max(0, d.getSeconds() + (d.getNano() > 0 ? 1 : 0));
        if (seconds < 60) {
            return seconds + " s";
        }
        long minutes = (seconds + 59) / 60;
        long h = minutes / 60;
        long m = minutes % 60;
        if (h == 0) {
            return m + " min";
        }
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    // ---- menu ----

    private void showMenu(Point ignored, MouseEvent e) {
        hideAnchor();
        controller.tick(Instant.now());
        refresh();
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
        Instant now = Instant.now();
        JPopupMenu menu = new JPopupMenu();
        menu.add(disabled(statusLine(now)));

        Snapshot snap = controller.snapshot();
        long working = snap.sessions().stream().filter(s -> s.status() == SessionStatus.WORKING).count();
        long waiting = snap.sessions().stream().filter(s -> s.status() == SessionStatus.WAITING).count();
        long unknown = snap.sessions().stream().filter(s -> s.status() == SessionStatus.UNKNOWN).count();
        menu.add(disabled(sessionsLine(working, waiting, unknown)));
        for (Session s : snap.sessions()) {
            menu.add(disabled("    " + s.name() + " — " + statusText(s.status())));
        }
        if (controller.hasWarning()) {
            menu.add(disabled("No se pudo leer el estado de Claude; se mantiene activo"));
        }
        if (controller.lastError() != null) {
            menu.add(disabled(controller.lastError()));
        }
        menu.addSeparator();

        JMenu timerMenu = new JMenu("Apagar tras");
        timerMenu.add(item("Sin límite", () -> controller.clearTimer()));
        timerMenu.add(item("30 minutos", () -> setTimer(30)));
        timerMenu.add(item("1 hora", () -> setTimer(60)));
        timerMenu.add(item("2 horas", () -> setTimer(120)));
        timerMenu.add(item("4 horas", () -> setTimer(240)));
        timerMenu.add(item("Personalizado…", this::customTimer));
        menu.add(timerMenu);

        JCheckBoxMenuItem auto = new JCheckBoxMenuItem("Apagar cuando Claude termine", controller.isAutoOff());
        auto.addActionListener(e -> {
            controller.setAutoOff(auto.isSelected(), Instant.now());
            refresh();
        });
        menu.add(auto);
        menu.addSeparator();
        menu.add(item("Salir", () -> {
            controller.shutdown();
            SystemTray.getSystemTray().remove(trayIcon);
            System.exit(0);
        }));
        return menu;
    }

    static String sessionsLine(long working, long waiting, long unknown) {
        String s = "Sesiones: " + working + " trabajando, " + waiting + " en espera";
        return unknown > 0 ? s + ", " + unknown + " desconocidas" : s;
    }

    private static String statusText(SessionStatus st) {
        return switch (st) {
            case WORKING -> "trabajando";
            case WAITING -> "en espera";
            case UNKNOWN -> "desconocido";
        };
    }

    private static JMenuItem disabled(String text) {
        JMenuItem i = new JMenuItem(text);
        i.setEnabled(false);
        return i;
    }

    private JMenuItem item(String text, Runnable action) {
        JMenuItem i = new JMenuItem(text);
        i.addActionListener(e -> {
            action.run();
            refresh();
        });
        return i;
    }

    private void setTimer(long minutes) {
        controller.setTimer(Duration.ofMinutes(minutes), Instant.now());
    }

    private void customTimer() {
        Object input = JOptionPane.showInputDialog(null, "Minutos:", "Apagar tras", JOptionPane.PLAIN_MESSAGE,
                null, null, "60");
        if (input == null) {
            return;
        }
        long minutes;
        try {
            minutes = Long.parseLong(input.toString().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, INVALID_MINUTES);
            return;
        }
        if (minutes <= 0) {
            JOptionPane.showMessageDialog(null, INVALID_MINUTES);
        } else if (minutes > MAX_CUSTOM_MINUTES) {
            JOptionPane.showMessageDialog(null, TOO_LARGE_MINUTES);
        } else {
            setTimer(minutes);
        }
    }
}
