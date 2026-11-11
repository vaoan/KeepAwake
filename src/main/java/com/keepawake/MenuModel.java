package com.keepawake;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The menu as data: rows, localized text (Messages) and actions, built from Controller state. The native helper and the Swing
 * popup both render it. Pure: no AWT, no I/O.
 */
final class MenuModel {
    private static final String CUSTOM_LABEL = Messages.get("timer.custom");
    private static final String NO_LIMIT_LABEL = Messages.get("timer.noLimit");
    private static final List<TimerPreset> TIMER_PRESETS = List.of(new TimerPreset(Messages.get("timer.30min"), 30),
            new TimerPreset(Messages.get("timer.1h"), 60), new TimerPreset(Messages.get("timer.2h"), 120),
            new TimerPreset(Messages.get("timer.4h"), 240));

    private MenuModel() {}

    private record TimerPreset(String label, long minutes) {
        boolean matches(Duration d) {
            return d.equals(Duration.ofMinutes(minutes));
        }
    }

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
            menu.add(Entry.disabled("    " + Messages.format("session.row", s.name(), statusText(s.status()))));
        }
        if (controller.hasWarning()) {
            menu.add(Entry.disabled(controller.isOn()
                    ? Messages.get("warning.unreadableActive")
                    : Messages.get("warning.unreadable")));
        }
        if (controller.lastError() != null) {
            menu.add(Entry.disabled(controller.lastError()));
        }
        menu.add(Entry.separator());

        menu.add(timerMenu(controller, onCustomTimer));

        boolean autoOn = controller.isAutoOn();
        menu.add(Entry.check(Messages.get("menu.autoOn"), autoOn,
                () -> controller.setAutoOn(!autoOn, Instant.now())));
        boolean autoOff = controller.isAutoOff();
        menu.add(Entry.check(Messages.get("menu.autoOff"), autoOff,
                () -> controller.setAutoOff(!autoOff, Instant.now())));
        menu.add(Entry.separator());
        menu.add(Entry.item(Messages.get("menu.quit"), onQuit));
        return menu;
    }

    /** Exactly one row is checked, and the submenu's own label names it so it shows without opening the submenu. */
    private static Entry timerMenu(Controller controller, Runnable onCustomTimer) {
        Duration selected = controller.selectedTimer().orElse(null);
        Optional<TimerPreset> preset = TIMER_PRESETS.stream()
                .filter(p -> selected != null && p.matches(selected)).findFirst();
        List<Entry> rows = new ArrayList<>();
        rows.add(Entry.check(NO_LIMIT_LABEL, selected == null, controller::clearTimer));
        for (TimerPreset p : TIMER_PRESETS) {
            rows.add(Entry.check(p.label(), preset.filter(p::equals).isPresent(),
                    () -> setTimer(controller, p.minutes())));
        }
        rows.add(Entry.check(CUSTOM_LABEL, selected != null && preset.isEmpty(), onCustomTimer));
        String current = selected == null ? NO_LIMIT_LABEL
                : preset.map(TimerPreset::label).orElseGet(() -> formatDuration(selected));
        return Entry.sub(Messages.format("timer.current", current), rows);
    }

    static void setTimer(Controller controller, long minutes) {
        controller.setTimer(Duration.ofMinutes(minutes), Instant.now());
    }

    static long count(Snapshot snap, SessionStatus... statuses) {
        return snap.sessions().stream().filter(s -> Arrays.asList(statuses).contains(s.status())).count();
    }

    static String statusLine(Controller controller, Instant now) {
        if (!controller.isOn()) {
            return Messages.get("status.inactive");
        }
        Optional<Duration> remaining = soonest(controller, now);
        return remaining.map(d -> Messages.format("status.activeUntil", formatDuration(d)))
                .orElse(Messages.get("status.active"));
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
        return Messages.get("sessions.label") + " " + sessionCounts(working, waiting, unknown);
    }

    /** "2 working, 1 waiting", plus ", 1 unknown" when any session is unknown. */
    static String sessionCounts(long working, long waiting, long unknown) {
        String s = Messages.format("sessions.counts", working, waiting);
        return unknown > 0 ? s + ", " + Messages.format("sessions.unknown", unknown) : s;
    }

    private static String statusText(SessionStatus st) {
        return switch (st) {
            case WORKING -> Messages.get("session.working");
            case BACKGROUND -> Messages.get("session.background");
            case WAITING -> Messages.get("session.waiting");
            case UNKNOWN -> Messages.get("session.unknown");
        };
    }
}
