package com.keepawake;

import java.util.OptionalLong;

/** Decides whether the process behind a session file is still the same live process. */
public interface ProcessProbe {
    boolean isAlive(long pid, OptionalLong startedAtMillis);
}
