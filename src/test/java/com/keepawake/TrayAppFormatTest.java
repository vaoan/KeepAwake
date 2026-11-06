package com.keepawake;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TrayAppFormatTest {
    @Test
    void hoursAndMinutes() {
        assertEquals("1 h 20 min", MenuModel.formatDuration(Duration.ofMinutes(80)));
    }

    @Test
    void exactHour() {
        assertEquals("2 h", MenuModel.formatDuration(Duration.ofHours(2)));
    }

    @Test
    void minutesOnly() {
        assertEquals("5 min", MenuModel.formatDuration(Duration.ofMinutes(5)));
    }

    @Test
    void secondsUnderOneMinute() {
        assertEquals("45 s", MenuModel.formatDuration(Duration.ofSeconds(45)));
    }

    @Test
    void roundsUpToNextMinute() {
        assertEquals("2 min", MenuModel.formatDuration(Duration.ofSeconds(61)));
        assertEquals("1 min", MenuModel.formatDuration(Duration.ofSeconds(60)));
    }
}
