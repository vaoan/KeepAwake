package com.keepawake;

/** Something that can hold the Mac awake. */
public interface Awake {
    /** Starts holding the Mac awake; true if running afterwards. */
    boolean start();

    void stop();

    boolean isRunning();
}
