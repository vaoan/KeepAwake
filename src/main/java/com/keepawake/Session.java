package com.keepawake;

/** One live interactive Claude Code session. */
public record Session(long pid, String name, SessionStatus status) {}
