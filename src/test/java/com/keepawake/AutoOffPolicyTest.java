package com.keepawake;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AutoOffPolicyTest {
    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private static Session s(long pid, SessionStatus st) {
        return new Session(pid, "claude", st);
    }

    private static Snapshot snap(Session... sessions) {
        return new Snapshot(List.of(sessions), true);
    }

    @Test
    void backgroundBlocksCountdown() {
        AutoOffPolicy p = new AutoOffPolicy();
        Snapshot b = snap(s(1, SessionStatus.BACKGROUND));
        assertTrue(b.hasWorking());
        assertFalse(p.shouldTurnOff(b, T0));
        assertFalse(p.shouldTurnOff(b, T0.plusSeconds(600)));
        assertTrue(p.remaining(T0.plusSeconds(600)).isEmpty());
    }

    @Test
    void allWaitingForTwoMinutesTurnsOff() {
        AutoOffPolicy p = new AutoOffPolicy();
        Snapshot w = snap(s(1, SessionStatus.WAITING));
        assertFalse(p.shouldTurnOff(w, T0));
        assertFalse(p.shouldTurnOff(w, T0.plusSeconds(119)));
        assertTrue(p.shouldTurnOff(w, T0.plusSeconds(120)));
    }

    @Test
    void noSessionsForTwoMinutesTurnsOff() {
        AutoOffPolicy p = new AutoOffPolicy();
        Snapshot e = snap();
        assertFalse(p.shouldTurnOff(e, T0));
        assertFalse(p.shouldTurnOff(e, T0.plusSeconds(119)));
        assertTrue(p.shouldTurnOff(e, T0.plusSeconds(120)));
    }

    @Test
    void busyRestartsCountdown() {
        AutoOffPolicy p = new AutoOffPolicy();
        Snapshot w = snap(s(1, SessionStatus.WAITING));
        assertFalse(p.shouldTurnOff(w, T0));
        assertFalse(p.shouldTurnOff(snap(s(1, SessionStatus.WORKING)), T0.plusSeconds(60)));
        assertFalse(p.shouldTurnOff(w, T0.plusSeconds(61)));
        assertFalse(p.shouldTurnOff(w, T0.plusSeconds(180)));
        assertTrue(p.shouldTurnOff(w, T0.plusSeconds(181)));
    }

    @Test
    void newBusySessionDuringGraceBlocks() {
        AutoOffPolicy p = new AutoOffPolicy();
        assertFalse(p.shouldTurnOff(snap(s(1, SessionStatus.WAITING)), T0));
        assertFalse(p.shouldTurnOff(
                snap(s(1, SessionStatus.WAITING), s(2, SessionStatus.WORKING)), T0.plusSeconds(100)));
        assertFalse(p.shouldTurnOff(snap(s(1, SessionStatus.WAITING)), T0.plusSeconds(130)));
    }

    @Test
    void newIdleSessionDoesNotBlock() {
        AutoOffPolicy p = new AutoOffPolicy();
        assertFalse(p.shouldTurnOff(snap(s(1, SessionStatus.WAITING)), T0));
        assertFalse(p.shouldTurnOff(
                snap(s(1, SessionStatus.WAITING), s(2, SessionStatus.WAITING)), T0.plusSeconds(60)));
        assertTrue(p.shouldTurnOff(
                snap(s(1, SessionStatus.WAITING), s(2, SessionStatus.WAITING)), T0.plusSeconds(120)));
    }

    @Test
    void unknownNeverTurnsOff() {
        AutoOffPolicy p = new AutoOffPolicy();
        Snapshot u = snap(s(1, SessionStatus.UNKNOWN));
        assertFalse(p.shouldTurnOff(u, T0));
        assertFalse(p.shouldTurnOff(u, T0.plus(Duration.ofHours(1))));
    }

    @Test
    void unreadableNeverTurnsOff() {
        AutoOffPolicy p = new AutoOffPolicy();
        Snapshot u = new Snapshot(List.of(), false);
        assertFalse(p.shouldTurnOff(u, T0));
        assertFalse(p.shouldTurnOff(u, T0.plus(Duration.ofHours(1))));
    }

    @Test
    void resetRestartsCountdown() {
        AutoOffPolicy p = new AutoOffPolicy();
        Snapshot w = snap(s(1, SessionStatus.WAITING));
        assertFalse(p.shouldTurnOff(w, T0));
        p.reset();
        assertFalse(p.shouldTurnOff(w, T0.plusSeconds(120)));
    }

    @Test
    void remainingTracksCountdown() {
        AutoOffPolicy p = new AutoOffPolicy();
        Snapshot w = snap(s(1, SessionStatus.WAITING));
        assertEquals(Optional.empty(), p.remaining(T0));
        p.shouldTurnOff(w, T0);
        assertEquals(Optional.of(Duration.ofSeconds(90)), p.remaining(T0.plusSeconds(30)));
        assertEquals(Optional.of(Duration.ZERO), p.remaining(T0.plusSeconds(500)));
        p.shouldTurnOff(snap(s(1, SessionStatus.WORKING)), T0.plusSeconds(40));
        assertEquals(Optional.empty(), p.remaining(T0.plusSeconds(41)));
    }
}
