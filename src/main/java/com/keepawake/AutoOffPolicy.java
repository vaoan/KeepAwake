package com.keepawake;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Decides when to turn keep-awake off: after GRACE of continuous quiet (no working or unknown sessions, readable). */
public class AutoOffPolicy {
    static final Duration GRACE = Duration.ofMinutes(2);

    private Instant quietSince;

    /** Records a snapshot taken at {@code now}; true once quiet for at least GRACE (stays true until reset). */
    public boolean shouldTurnOff(Snapshot s, Instant now) {
        if (!s.readable() || s.hasWorking() || s.hasUnknown()) {
            quietSince = null;
            return false;
        }
        if (quietSince == null) {
            quietSince = now;
        }
        return Duration.between(quietSince, now).compareTo(GRACE) >= 0;
    }

    public void reset() {
        quietSince = null;
    }

    /** Time left before turn-off, or empty when no countdown is running. */
    public Optional<Duration> remaining(Instant now) {
        if (quietSince == null) {
            return Optional.empty();
        }
        Duration left = GRACE.minus(Duration.between(quietSince, now));
        return Optional.of(left.isNegative() ? Duration.ZERO : left);
    }
}
