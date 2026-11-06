package com.keepawake;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Keep-awake state machine: on/off, optional timer, optional auto-off. Not thread-safe; drive from one thread. */
public class Controller {
    static final String ERR_DIED = "caffeinate se detuvo inesperadamente";
    static final String ERR_START = "No se pudo iniciar caffeinate";

    private final Awake awake;
    private final Supplier<Snapshot> scan;
    private final Runnable onChange;
    private final AutoOffPolicy policy = new AutoOffPolicy();

    private boolean on;
    private boolean autoOff;
    private Instant deadline;
    private String lastError;
    private Snapshot snapshot;

    public Controller(Awake awake, Supplier<Snapshot> scan, Runnable onChange) {
        this.awake = awake;
        this.scan = scan;
        this.onChange = onChange;
        this.snapshot = scan.get();
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
        }
        notifyIfChanged(before);
    }

    public void clearTimer() {
        State before = state();
        deadline = null;
        notifyIfChanged(before);
    }

    public void setAutoOff(boolean enabled, Instant now) {
        State before = state();
        autoOff = enabled;
        if (!enabled) {
            policy.reset();
        } else if (on) {
            snapshot = scan.get();
            policy.shouldTurnOff(snapshot, now);
        }
        notifyIfChanged(before);
    }

    public void tick(Instant now) {
        State before = state();
        snapshot = scan.get();
        if (on && !awake.isRunning()) {
            turnOff();
            lastError = ERR_DIED;
        } else if (on && deadline != null && !now.isBefore(deadline)) {
            turnOff();
        } else if (on && autoOff && policy.shouldTurnOff(snapshot, now)) {
            turnOff();
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

    public Optional<Instant> deadline() {
        return Optional.ofNullable(deadline);
    }

    public Optional<Duration> autoOffRemaining(Instant now) {
        return on && autoOff ? policy.remaining(now) : Optional.empty();
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public boolean hasWarning() {
        return !snapshot.readable() || snapshot.hasUnknown();
    }

    public String lastError() {
        return lastError;
    }

    private void turnOn(Instant now) {
        if (!awake.start()) {
            lastError = ERR_START;
            return;
        }
        on = true;
        lastError = null;
        policy.reset();
        snapshot = scan.get();
        if (autoOff) {
            policy.shouldTurnOff(snapshot, now);
        }
    }

    private void turnOff() {
        awake.stop();
        on = false;
        deadline = null;
        policy.reset();
    }

    private record State(boolean on, boolean autoOff, Instant deadline, String error, Snapshot snapshot) {}

    private State state() {
        return new State(on, autoOff, deadline, lastError, snapshot);
    }

    private void notifyIfChanged(State before) {
        if (!Objects.equals(before, state())) {
            onChange.run();
        }
    }
}
