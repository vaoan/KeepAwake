package com.keepawake;

import java.util.List;

/** Result of one scan. {@code readable} is false when the sessions directory could not be listed. */
public record Snapshot(List<Session> sessions, boolean readable) {
    public Snapshot {
        sessions = List.copyOf(sessions);
    }

    public boolean hasWorking() {
        return sessions.stream().anyMatch(s -> s.status() == SessionStatus.WORKING);
    }

    public boolean hasUnknown() {
        return sessions.stream().anyMatch(s -> s.status() == SessionStatus.UNKNOWN);
    }
}
