package com.keepawake;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {
    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @TempDir Path dir;

    @Test
    void missingFileIsBothOff() {
        assertEquals(Settings.DEFAULT, Settings.load(dir.resolve("settings.properties")));
    }

    @Test
    void saveThenLoadRoundTrips() throws IOException {
        Path file = dir.resolve("sub").resolve("settings.properties");
        for (Settings s : new Settings[] {new Settings(true, false), new Settings(false, true), new Settings(true, true)}) {
            s.save(file);
            assertEquals(s, Settings.load(file));
        }
        assertFalse(Files.exists(file.resolveSibling("settings.properties.tmp")));
    }

    @Test
    void garbageIsBothOff() throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "autoOn=quizá\n\\u12");
        assertEquals(Settings.DEFAULT, Settings.load(file));
    }

    @Test
    void applySetsBothOptionsAndAutoOnActsAtOnce() {
        ControllerTest.FakeAwake awake = new ControllerTest.FakeAwake();
        Controller c = new Controller(awake, () -> ControllerTest.of(SessionStatus.WORKING), () -> { });
        new Settings(true, true).apply(c, T0);
        assertTrue(c.isAutoOn());
        assertTrue(c.isAutoOff());
        assertTrue(c.isOn());
        assertEquals(new Settings(true, true), Settings.of(c));
    }
}
