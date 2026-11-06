package com.keepawake;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The menu as data: rows, Spanish text and actions, built from Controller state. The native helper and the Swing
 * popup both render it. Pure: no AWT, no I/O.
 */
final class MenuModel {
    private static final String CUSTOM_LABEL = "Personalizado…";

    private MenuModel() {}

    /**
     * One row. {@code label} is null for a separator. {@code checked} is null when the row has no check mark.
     * Rows with children are submenus (no action). {@code action} is null for rows that do nothing.
     */
    record Entry(String label, boolean enabled, Boolean checked, List<Entry> children, Runnable action) {
        Entry {
            children = List.copyOf(children);
        }

        static Entry separator() {
            return new Entry(null, false, null, List.of(), null);
        }

        static Entry disabled(String label) {
            return new Entry(label, false, null, List.of(), null);
        }

        static Entry item(String label, Runnable action) {
            return new Entry(label, true, null, List.of(), action);
        }

        static Entry check(String label, boolean checked, Runnable action) {
            return new Entry(label, true, checked, List.of(), action);
        }

        static Entry sub(String label, List<Entry> children) {
            return new Entry(label, true, null, children, null);
        }

        boolean isSeparator() {
            return label == null;
        }

        boolean isSubmenu() {
            return !children.isEmpty();
        }
    }

    /** Every entry in pre-order (a submenu, then its children): the index in this list is the entry's tag. */
    static List<Entry> flatten(List<Entry> entries) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            out.add(e);
            out.addAll(flatten(e.children()));
        }
        return out;
    }

    /** {@code onCustomTimer} opens the custom-minutes dialog; {@code onQuit} quits the app. */
    static List<Entry> build(Controller controller, Instant now, Runnable onCustomTimer, Runnable onQuit) {
        List<Entry> menu = new ArrayList<>();
        menu.add(Entry.disabled(statusLine(controller, now)));

        Snapshot snap = controller.snapshot();
        long working = count(snap, SessionStatus.WORKING, SessionStatus.BACKGROUND);
        long waiting = count(snap, SessionStatus.WAITING);
        long unknown = count(snap, SessionStatus.UNKNOWN);
        menu.add(Entry.disabled(sessionsLine(working, waiting, unknown)));
        for (Session s : snap.sessions()) {
            menu.add(Entry.disabled("    " + s.name() + " — " + statusText(s.status())));
        }
        if (controller.hasWarning()) {
            menu.add(Entry.disabled(controller.isOn()
                    ? "No se pudo leer el estado de Claude; se mantiene activo"
                    : "No se pudo leer el estado de Claude"));
        }
        if (controller.lastError() != null) {
            menu.add(Entry.disabled(controller.lastError()));
        }
        menu.add(Entry.separator());

        menu.add(Entry.sub("Apagar tras", List.of(
                Entry.item("Sin límite", controller::clearTimer),
                Entry.item("30 minutos", () -> setTimer(controller, 30)),
                Entry.item("1 hora", () -> setTimer(controller, 60)),
                Entry.item("2 horas", () -> setTimer(controller, 120)),
                Entry.item("4 horas", () -> setTimer(controller, 240)),
                Entry.item(CUSTOM_LABEL, onCustomTimer))));

        boolean autoOff = controller.isAutoOff();
        menu.add(Entry.check("Apagar cuando Claude termine", autoOff,
                () -> controller.setAutoOff(!autoOff, Instant.now())));
        menu.add(Entry.separator());
        menu.add(Entry.item("Salir", onQuit));
        return menu;
    }

    static void setTimer(Controller controller, long minutes) {
        controller.setTimer(Duration.ofMinutes(minutes), Instant.now());
    }

    static long count(Snapshot snap, SessionStatus... statuses) {
        return snap.sessions().stream().filter(s -> Arrays.asList(statuses).contains(s.status())).count();
    }

    static String statusLine(Controller controller, Instant now) {
        if (!controller.isOn()) {
            return "Inactivo";
        }
        Optional<Duration> remaining = soonest(controller, now);
        return remaining.map(d -> "Activo · apaga en " + formatDuration(d)).orElse("Activo");
    }

    private static Optional<Duration> soonest(Controller controller, Instant now) {
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

    static String sessionsLine(long working, long waiting, long unknown) {
        String s = "Sesiones: " + working + " trabajando, " + waiting + " en espera";
        return unknown > 0 ? s + ", " + unknown + " desconocidas" : s;
    }

    private static String statusText(SessionStatus st) {
        return switch (st) {
            case WORKING -> "trabajando";
            case BACKGROUND -> "shell en segundo plano";
            case WAITING -> "en espera";
            case UNKNOWN -> "desconocido";
        };
    }
}
