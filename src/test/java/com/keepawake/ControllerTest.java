package com.keepawake;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ControllerTest {
    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    static class FakeAwake implements Awake {
        boolean running;
        boolean failStart;
        int starts;
        int stops;

        public boolean start() {
            if (running) return true;
            if (failStart) return false;
            running = true;
            starts++;
            return true;
        }

        public void stop() {
            if (running) stops++;
            running = false;
        }

        public boolean isRunning() {
            return running;
        }
    }

    FakeAwake awake;
    Snapshot snap;
    int changes;
    Controller c;

    static Snapshot of(SessionStatus... statuses) {
        return new Snapshot(java.util.Arrays.stream(statuses).map(ControllerTest::session).toList(), true);
    }

    static Session session(SessionStatus st) {
        return new Session(1, "claude", st);
    }

    @BeforeEach
    void setUp() {
        awake = new FakeAwake();
        snap = of(SessionStatus.WAITING);
        changes = 0;
        c = new Controller(awake, () -> snap, () -> changes++);
    }

    /** Ticks at 30 s steps strictly after {@code from}, ending with a tick at from+untilSeconds. */
    void tickEvery30s(Instant from, int untilSeconds) {
        for (int t = 0; t < untilSeconds; t += 30) {
            c.tick(from.plusSeconds(t));
        }
        c.tick(from.plusSeconds(untilSeconds));
    }

    @Test
    void toggleStartsAndStops() {
        c.toggle(T0);
        assertTrue(c.isOn());
        assertTrue(awake.running);
        c.toggle(T0.plusSeconds(1));
        assertFalse(c.isOn());
        assertFalse(awake.running);
    }

    @Test
    void timerTurnsOffAtDeadline() {
        c.toggle(T0);
        c.setTimer(Duration.ofHours(1), T0);
        assertEquals(T0.plus(Duration.ofHours(1)), c.deadline().orElseThrow());
        c.tick(T0.plus(Duration.ofMinutes(59)));
        assertTrue(c.isOn());
        c.tick(T0.plus(Duration.ofMinutes(60)));
        assertFalse(c.isOn());
        assertTrue(c.deadline().isEmpty());
    }

    @Test
    void settingTimerWhileOffTurnsOn() {
        c.setTimer(Duration.ofMinutes(30), T0);
        assertTrue(c.isOn());
        assertTrue(c.deadline().isPresent());
    }

    @Test
    void setTimerStartFailureStaysOff() {
        awake.failStart = true;
        c.setTimer(Duration.ofMinutes(30), T0);
        assertFalse(c.isOn());
        assertTrue(c.deadline().isEmpty());
        assertNotNull(c.lastError());
    }

    @Test
    void clearTimerKeepsState() {
        c.setTimer(Duration.ofMinutes(30), T0);
        c.clearTimer();
        assertTrue(c.isOn());
        assertTrue(c.deadline().isEmpty());
    }

    @Test
    void autoOffTurnsOffAfterGrace() {
        c.setAutoOff(true, T0);
        c.toggle(T0);
        tickEvery30s(T0, 119);
        assertTrue(c.isOn());
        c.tick(T0.plusSeconds(120));
        assertFalse(c.isOn());
        assertTrue(c.isAutoOff());
    }

    @Test
    void autoOffAlreadyQuietStillWaitsGrace() {
        c.toggle(T0);
        c.setAutoOff(true, T0);
        c.tick(T0.plusSeconds(119));
        assertTrue(c.isOn());
        c.tick(T0.plusSeconds(120));
        assertFalse(c.isOn());
    }

    @Test
    void autoOffRemainingOnlyWhenOnAndEnabled() {
        assertTrue(c.autoOffRemaining(T0).isEmpty());
        c.setAutoOff(true, T0);
        assertTrue(c.autoOffRemaining(T0).isEmpty());
        c.toggle(T0);
        assertEquals(Duration.ofSeconds(60), c.autoOffRemaining(T0.plusSeconds(60)).orElseThrow());
        c.setAutoOff(false, T0);
        assertTrue(c.autoOffRemaining(T0).isEmpty());
    }

    @Test
    void autoOffDisabledIgnoresSessions() {
        c.toggle(T0);
        c.tick(T0);
        c.tick(T0.plus(Duration.ofHours(1)));
        assertTrue(c.isOn());
    }

    @Test
    void timerFiresWhileSessionBusy() {
        snap = of(SessionStatus.WORKING);
        c.setTimer(Duration.ofMinutes(10), T0);
        c.setAutoOff(true, T0);
        c.tick(T0.plus(Duration.ofMinutes(10)));
        assertFalse(c.isOn());
    }

    @Test
    void turningBackOnRestartsGrace() {
        c.setAutoOff(true, T0);
        c.toggle(T0);
        c.tick(T0.plusSeconds(120));
        assertFalse(c.isOn());
        Instant t1 = T0.plusSeconds(500);
        c.toggle(t1);
        c.tick(t1.plusSeconds(119));
        assertTrue(c.isOn());
    }

    @Test
    void startFailureStaysOffWithError() {
        awake.failStart = true;
        c.toggle(T0);
        assertFalse(c.isOn());
        assertEquals("No se pudo iniciar caffeinate", c.lastError());
    }

    @Test
    void successfulStartClearsError() {
        awake.failStart = true;
        c.toggle(T0);
        awake.failStart = false;
        c.toggle(T0);
        assertTrue(c.isOn());
        assertNull(c.lastError());
    }

    @Test
    void deadCaffeinateIsDetectedOnTick() {
        c.toggle(T0);
        awake.running = false;
        c.tick(T0.plusSeconds(1));
        assertFalse(c.isOn());
        assertEquals("caffeinate se detuvo inesperadamente", c.lastError());
    }

    @Test
    void warningWhenUnknownOrUnreadable() {
        assertNotNull(c.snapshot());
        assertFalse(c.hasWarning());
        snap = of(SessionStatus.UNKNOWN);
        c.tick(T0);
        assertTrue(c.hasWarning());
        snap = new Snapshot(List.of(), false);
        c.tick(T0);
        assertTrue(c.hasWarning());
        snap = of(SessionStatus.WORKING);
        c.tick(T0);
        assertFalse(c.hasWarning());
    }

    @Test
    void idleTickWithIdenticalSnapshotDoesNotNotify() {
        c.toggle(T0);
        int before = changes;
        snap = of(SessionStatus.WAITING);
        c.tick(T0.plusSeconds(1));
        c.tick(T0.plusSeconds(2));
        assertEquals(before, changes);
        snap = of(SessionStatus.WORKING);
        c.tick(T0.plusSeconds(3));
        assertEquals(before + 1, changes);
    }

    @Test
    void autoOffToggleNotifies() {
        c.setAutoOff(true, T0);
        assertEquals(1, changes);
    }

    @Test
    void shutdownStopsAwake() {
        c.toggle(T0);
        c.shutdown();
        assertFalse(awake.running);
    }

    @Test
    void gapBetweenTicksRestartsGrace() {
        c.setAutoOff(true, T0);
        c.toggle(T0);
        c.tick(T0);
        Instant wake = T0.plus(Duration.ofHours(1));
        c.tick(wake);
        assertTrue(c.isOn());
        // regular ticks after the wake-up
        for (int t = 30; t < 119; t += 30) c.tick(wake.plusSeconds(t));
        c.tick(wake.plusSeconds(119));
        assertTrue(c.isOn());
        c.tick(wake.plusSeconds(120));
        assertFalse(c.isOn());
    }

    @Test
    void deadlinePassedDuringSleepStillFires() {
        c.setTimer(Duration.ofMinutes(10), T0);
        c.tick(T0);
        c.tick(T0.plus(Duration.ofHours(1)));
        assertFalse(c.isOn());
    }

    @Test
    void throwingScanIsUnreadableAndTimerStillFires() {
        boolean[] fail = {false};
        c = new Controller(awake, () -> {
            if (fail[0]) throw new IllegalStateException("boom");
            return snap;
        }, () -> changes++);
        c.setTimer(Duration.ofMinutes(10), T0);
        fail[0] = true;
        c.tick(T0.plusSeconds(1));
        assertTrue(c.isOn());
        assertFalse(c.snapshot().readable());
        assertTrue(c.hasWarning());
        c.tick(T0.plus(Duration.ofMinutes(10)));
        assertFalse(c.isOn());
    }

    @Test
    void autoOnTurnsOnWhenASessionStartsWorking() {
        c.setAutoOn(true, T0);
        assertFalse(c.isOn());
        snap = of(SessionStatus.WAITING, SessionStatus.WORKING);
        c.tick(T0.plusSeconds(3));
        assertTrue(c.isOn());
        assertTrue(awake.running);
    }

    @Test
    void autoOnCountsBackgroundShellAsWorking() {
        c.setAutoOn(true, T0);
        snap = of(SessionStatus.BACKGROUND);
        c.tick(T0.plusSeconds(3));
        assertTrue(c.isOn());
    }

    @Test
    void autoOnIgnoresWaitingAndUnknownSessions() {
        c.setAutoOn(true, T0);
        snap = of(SessionStatus.WAITING, SessionStatus.UNKNOWN);
        c.tick(T0.plusSeconds(3));
        assertFalse(c.isOn());
    }

    @Test
    void enablingAutoOnWhileWorkingTurnsOnAtOnce() {
        snap = of(SessionStatus.WORKING);
        c.setAutoOn(true, T0);
        assertTrue(c.isOn());
        assertTrue(c.isAutoOn());
    }

    @Test
    void autoOnDisabledIgnoresWork() {
        snap = of(SessionStatus.WORKING);
        c.tick(T0);
        assertFalse(c.isOn());
    }

    @Test
    void manualOffWhileWorkingStaysOffUntilTheWorkEnds() {
        c.setAutoOn(true, T0);
        snap = of(SessionStatus.WORKING);
        c.tick(T0.plusSeconds(3));
        c.toggle(T0.plusSeconds(4));
        c.tick(T0.plusSeconds(6));
        assertFalse(c.isOn());

        snap = of(SessionStatus.WAITING);
        c.tick(T0.plusSeconds(9));
        assertFalse(c.isOn());
        snap = of(SessionStatus.WORKING);
        c.tick(T0.plusSeconds(12));
        assertTrue(c.isOn());
    }

    @Test
    void timerExpiryWhileWorkingStaysOff() {
        c.setAutoOn(true, T0);
        snap = of(SessionStatus.WORKING);
        c.setTimer(Duration.ofMinutes(1), T0);
        c.tick(T0.plusSeconds(60));
        assertFalse(c.isOn());
        c.tick(T0.plusSeconds(63));
        assertFalse(c.isOn());
    }

    @Test
    void autoOnStartFailureIsNotRetriedEveryTick() {
        awake.failStart = true;
        c.setAutoOn(true, T0);
        snap = of(SessionStatus.WORKING);
        c.tick(T0.plusSeconds(3));
        assertFalse(c.isOn());
        assertEquals(Controller.ERR_START, c.lastError());
        awake.failStart = false;
        c.tick(T0.plusSeconds(6));
        assertFalse(c.isOn());
    }

    @Test
    void autoOnAndAutoOffFollowTheWork() {
        c.setAutoOn(true, T0);
        c.setAutoOff(true, T0);
        snap = of(SessionStatus.WORKING);
        c.tick(T0);
        assertTrue(c.isOn());
        snap = of(SessionStatus.WAITING);
        tickEvery30s(T0.plusSeconds(30), 120);
        assertFalse(c.isOn());
        snap = of(SessionStatus.WORKING);
        c.tick(T0.plusSeconds(180));
        assertTrue(c.isOn());
    }

    @Test
    void disablingAutoOnLeavesItOn() {
        snap = of(SessionStatus.WORKING);
        c.setAutoOn(true, T0);
        c.setAutoOn(false, T0.plusSeconds(1));
        assertTrue(c.isOn());
        assertFalse(c.isAutoOn());
    }
}
