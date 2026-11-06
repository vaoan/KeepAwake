package com.keepawake;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionScannerTest {

    @TempDir Path dir;

    private void write(String file, String content) throws IOException {
        Files.writeString(dir.resolve(file), content);
    }

    private static String json(long pid, String kind, String status) {
        StringBuilder sb = new StringBuilder("{\"pid\":" + pid + ",\"startedAt\":1000");
        if (kind != null) sb.append(",\"kind\":\"").append(kind).append("\"");
        if (status != null) sb.append(",\"status\":\"").append(status).append("\"");
        return sb.append(",\"name\":\"n").append(pid).append("\"}").toString();
    }

    private SessionScanner scanner(boolean alive) {
        return new SessionScanner(dir, (pid, started) -> alive);
    }

    @Test void busyIsWorkingAndIdleIsWaiting() throws IOException {
        write("1.json", json(1, "interactive", "busy"));
        write("2.json", json(2, "interactive", "idle"));
        List<Session> s = scanner(true).scan().sessions();
        assertEquals(2, s.size());
        for (Session x : s) {
            assertEquals(x.pid() == 1 ? SessionStatus.WORKING : SessionStatus.WAITING, x.status());
        }
    }

    @Test void shellIsBackground() throws IOException {
        write("1.json", json(1, "interactive", "shell"));
        assertEquals(SessionStatus.BACKGROUND, scanner(true).scan().sessions().get(0).status());
    }

    @Test void waitingIsWaiting() throws IOException {
        write("1.json", json(1, "interactive", "waiting"));
        assertEquals(SessionStatus.WAITING, scanner(true).scan().sessions().get(0).status());
    }

    @Test void otherOrMissingStatusIsUnknown() throws IOException {
        write("1.json", json(1, "interactive", "paused"));
        write("2.json", json(2, "interactive", null));
        Snapshot snap = scanner(true).scan();
        assertEquals(2, snap.sessions().size());
        assertTrue(snap.sessions().stream().allMatch(x -> x.status() == SessionStatus.UNKNOWN));
        assertTrue(snap.hasUnknown());
        assertFalse(snap.hasWorking());
    }

    @Test void newFileAppearsOnNextScan() throws IOException {
        write("1.json", json(1, "interactive", "busy"));
        SessionScanner sc = scanner(true);
        assertEquals(1, sc.scan().sessions().size());
        write("2.json", json(2, "interactive", "busy"));
        assertEquals(2, sc.scan().sessions().size());
    }

    @Test void deletedFileDisappearsOnNextScan() throws IOException {
        write("1.json", json(1, "interactive", "busy"));
        write("2.json", json(2, "interactive", "busy"));
        SessionScanner sc = scanner(true);
        assertEquals(2, sc.scan().sessions().size());
        Files.delete(dir.resolve("2.json"));
        assertEquals(1, sc.scan().sessions().size());
    }

    @Test void deadProcessIsDropped() throws IOException {
        write("1.json", json(1, "interactive", "busy"));
        Snapshot snap = scanner(false).scan();
        assertTrue(snap.sessions().isEmpty());
        assertTrue(snap.readable());
    }

    @Test void nonInteractiveIsIgnored() throws IOException {
        write("1.json", json(1, "sdk", "busy"));
        assertTrue(scanner(true).scan().sessions().isEmpty());
    }

    @Test void bgIsCounted() throws IOException {
        write("1.json", json(1, "bg", "busy"));
        List<Session> s = scanner(true).scan().sessions();
        assertEquals(1, s.size());
        assertEquals(SessionStatus.WORKING, s.get(0).status());
    }

    @Test void daemonKindsAreIgnored() throws IOException {
        write("1.json", json(1, "daemon", "busy"));
        write("2.json", json(2, "daemon-worker", "busy"));
        write("3.json", json(3, "sdk", "busy"));
        assertTrue(scanner(true).scan().sessions().isEmpty());
    }

    @Test void missingKindCountsAsInteractive() throws IOException {
        write("1.json", json(1, null, "idle"));
        List<Session> s = scanner(true).scan().sessions();
        assertEquals(1, s.size());
        assertEquals(SessionStatus.WAITING, s.get(0).status());
    }

    @Test void corruptFileWithLivePidIsUnknown() throws IOException {
        write("123.json", "{\"pid\":12");
        List<Session> s = scanner(true).scan().sessions();
        assertEquals(1, s.size());
        assertEquals(123, s.get(0).pid());
        assertEquals("123", s.get(0).name());
        assertEquals(SessionStatus.UNKNOWN, s.get(0).status());
    }

    @Test void nonObjectJsonIsCorrupt() throws IOException {
        write("5.json", "[1,2]");
        List<Session> s = scanner(true).scan().sessions();
        assertEquals(1, s.size());
        assertEquals("5", s.get(0).name());
        assertEquals(SessionStatus.UNKNOWN, s.get(0).status());
    }

    @Test void corruptFileWithDeadPidIsDropped() throws IOException {
        write("123.json", "{\"pid\":12");
        assertTrue(scanner(false).scan().sessions().isEmpty());
    }

    @Test void filenamePidWinsOverJsonPid() throws IOException {
        write("7.json", json(99, "interactive", "busy"));
        assertEquals(7, scanner(true).scan().sessions().get(0).pid());
    }

    @Test void nameComesFromFileOrFallsBackToPid() throws IOException {
        write("7.json", json(7, "interactive", "busy"));
        write("8.json", "{\"kind\":\"interactive\",\"status\":\"busy\"}");
        for (Session x : scanner(true).scan().sessions()) {
            assertEquals(x.pid() == 7 ? "n7" : "8", x.name());
        }
    }

    @Test void keyFilesAreIgnored() throws IOException {
        write("123.abc.key", "secret");
        write("notes.json", "{}");
        assertTrue(scanner(true).scan().sessions().isEmpty());
    }

    @Test void emptyDirIsReadable() {
        Snapshot snap = scanner(true).scan();
        assertTrue(snap.readable());
        assertTrue(snap.sessions().isEmpty());
    }

    @Test void missingDirIsNotReadable() {
        Snapshot snap = new SessionScanner(dir.resolve("nope"), (p, s) -> true).scan();
        assertFalse(snap.readable());
        assertTrue(snap.sessions().isEmpty());
    }

    @Test void passesStartedAtToProbe() throws IOException {
        write("1.json", "{\"startedAt\":4242,\"kind\":\"interactive\",\"status\":\"busy\"}");
        write("2.json", "{broken");
        AtomicReference<OptionalLong> seen1 = new AtomicReference<>();
        AtomicReference<OptionalLong> seen2 = new AtomicReference<>();
        new SessionScanner(dir, (pid, started) -> {
            (pid == 1 ? seen1 : seen2).set(started);
            return true;
        }).scan();
        assertEquals(OptionalLong.of(4242), seen1.get());
        assertEquals(OptionalLong.empty(), seen2.get());
    }

    @Test void systemProbeRejectsReusedPid() {
        long start = ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli();
        ProcessProbe p = SessionScanner.systemProbe();
        assertFalse(p.isAlive(ProcessHandle.current().pid(), OptionalLong.of(start - 3_600_000)));
    }

    @Test void systemProbeAcceptsCurrentProcess() {
        long start = ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli();
        ProcessProbe p = SessionScanner.systemProbe();
        assertTrue(p.isAlive(ProcessHandle.current().pid(), OptionalLong.of(start)));
        assertTrue(p.isAlive(ProcessHandle.current().pid(), OptionalLong.empty()));
    }

    @Test void systemProbeRejectsDeadPid() {
        assertFalse(SessionScanner.systemProbe().isAlive(Long.MAX_VALUE, OptionalLong.empty()));
    }
}
