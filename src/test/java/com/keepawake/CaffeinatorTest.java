package com.keepawake;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CaffeinatorTest {
    @Test
    void startRunsAndStopTerminates() {
        Caffeinator c = new Caffeinator(List.of("sleep", "30"));
        assertTrue(c.start());
        assertTrue(c.isRunning());
        assertTrue(c.start(), "second start is a no-op success");
        c.stop();
        assertFalse(c.isRunning());
        c.stop();
    }

    @Test
    void missingBinaryReturnsFalse() {
        Caffeinator c = new Caffeinator(List.of("/nonexistent"));
        assertFalse(c.start());
        assertFalse(c.isRunning());
    }

    @Test
    void exitedProcessIsNotRunning() throws InterruptedException {
        Caffeinator c = new Caffeinator(List.of("true"));
        assertTrue(c.start());
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (c.isRunning() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(c.isRunning());
    }
}
