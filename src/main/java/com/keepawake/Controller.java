package com.keepawake;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Keep-awake state machine: on/off, optional timer, optional auto-on and auto-off. Not thread-safe; drive from one thread. */
public class Controller {
    static final String ERR_DIED = "caffeinate se detuvo inesperadamente";
    static final Duration SLEEP_GAP = Duration.ofSeconds(30);
    static final String ERR_START = "No se pudo iniciar caffeinate";

    private final Awake awake;
    private final Supplier<Snapshot> scan;
    private final Runnable onChange;
    private final AutoOffPolicy policy = new AutoOffPolicy();

    private boolean on;
    private boolean autoOff;
    private boolean autoOn;
    /** Auto-on already acted on (or was overruled in) the current stretch of work; cleared when no session works. */
    private boolean autoOnSpent;
    private Instant deadline;
    private Duration timer;
    private String lastError;
    private Snapshot snapshot;
    private Instant lastTick;
    private boolean autoOffNotice;
    private boolean autoOnNotice;

    public Controller(Awake awake, Supplier<Snapshot> scan, Runnable onChange) {
        this.awake = awake;
        this.scan = scan;
        this.onChange = onChange;
        this.snapshot = safeScan();
    }

    public void toggle(Instant now) {
        State before = state();
        if (on) {
            turnOff();
        } else {
            turnOn(now);
        }
        notifyIfChanged(before);
    }

    public void setTimer(Duration d, Instant now) {
        State before = state();
        if (!on) {
            turnOn(now);
        }
        if (on) {
            deadline = now.plus(d);
            timer = d;
        }
        notifyIfChanged(before);
    }

    public void clearTimer() {
        State before = state();
        deadline = null;
        timer = null;
        notifyIfChanged(before);
    }

    public void setAutoOff(boolean enabled, Instant now) {
        State before = state();
        autoOff = enabled;
        if (!enabled) {
            policy.reset();
        } else if (on) {
            snapshot = safeScan();
            policy.shouldTurnOff(snapshot, now);
        }
        notifyIfChanged(before);
    }

    /** While enabled, turns on whenever a session starts working, and at once if one already is. */
    public void setAutoOn(boolean enabled, Instant now) {
        State before = state();
        autoOn = enabled;
        autoOnSpent = false;
        if (enabled) {
            snapshot = safeScan();
            maybeAutoOn(now);
        }
        notifyIfChanged(before);
    }

    public void tick(Instant now) {
        State before = state();
        if (lastTick != null && Duration.between(lastTick, now).compareTo(SLEEP_GAP) > 0) {
            policy.reset();
        }
        lastTick = now;
        snapshot = safeScan();
        if (on && !awake.isRunning()) {
            turnOff();
            lastError = ERR_DIED;
        } else if (on && deadline != null && !now.isBefore(deadline)) {
            turnOff();
        } else if (on && autoOff && policy.shouldTurnOff(snapshot, now)) {
            turnOff();
            autoOffNotice = true;
        } else {
            maybeAutoOn(now);
        }
        notifyIfChanged(before);
    }

    public void shutdown() {
        awake.stop();
        on = false;
    }

    public boolean isOn() {
        return on;
    }

    public boolean isAutoOff() {
        return autoOff;
    }

    public boolean isAutoOn() {
        return autoOn;
    }

    public Optional<Instant> deadline() {
        return Optional.ofNullable(deadline);
    }

    /** The duration last chosen for the running timer; empty when there is no timer. */
    public Optional<Duration> selectedTimer() {
        return Optional.ofNullable(timer);
    }

    public Optional<Duration> autoOffRemaining(Instant now) {
        return on && autoOff ? policy.remaining(now) : Optional.empty();
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    /** True once after the auto-off policy turned keep-awake off; false afterwards. */
    public boolean consumeAutoOffNotice() {
        boolean n = autoOffNotice;
        autoOffNotice = false;
        return n;
    }

    /** True once after auto-on turned keep-awake on; false afterwards. */
    public boolean consumeAutoOnNotice() {
        boolean n = autoOnNotice;
        autoOnNotice = false;
        return n;
    }

    public boolean hasWarning() {
        return !snapshot.readable() || snapshot.hasUnknown();
    }

    public String lastError() {
        return lastError;
    }

    private Snapshot safeScan() {
        try {
            return scan.get();
        } catch (RuntimeException e) {
            return new Snapshot(List.of(), false);
        }
    }

    /**
     * Turns on once per stretch of work: after a manual off, a timer or a failure while Claude works, it waits until
     * no session works before it can act again.
     */
    private void maybeAutoOn(Instant now) {
        if (!snapshot.hasWorking()) {
            autoOnSpent = false;
        } else if (on) {
            autoOnSpent = true;
        } else if (autoOn && !autoOnSpent) {
            autoOnSpent = true;
            turnOn(now);
            autoOnNotice = on;
        }
    }

    private void turnOn(Instant now) {
        lastTick = null;
        if (!awake.start()) {
            lastError = ERR_START;
            return;
        }
        on = true;
        lastError = null;
        policy.reset();
        snapshot = safeScan();
        if (autoOff) {
            policy.shouldTurnOff(snapshot, now);
        }
    }

    private void turnOff() {
        if (snapshot.hasWorking()) {
            autoOnSpent = true;
        }
        awake.stop();
        on = false;
        deadline = null;
        timer = null;
        lastTick = null;
        policy.reset();
    }

    private record State(boolean on, boolean autoOff, boolean autoOn, Instant deadline, String error,
            Snapshot snapshot) {}

    private State state() {
        return new State(on, autoOff, autoOn, deadline, lastError, snapshot);
    }

    private void notifyIfChanged(State before) {
        if (!Objects.equals(before, state())) {
            onChange.run();
        }
    }
}
