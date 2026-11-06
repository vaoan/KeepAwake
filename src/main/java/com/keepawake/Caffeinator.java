package com.keepawake;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Holds the Mac awake by running a child process such as caffeinate. */
public class Caffeinator implements Awake {
    private final List<String> command;
    private Process process;

    public Caffeinator(List<String> command) {
        this.command = List.copyOf(command);
    }

    /** caffeinate that prevents display and idle sleep and exits when this JVM does. */
    public static Caffeinator system() {
        return new Caffeinator(List.of("caffeinate", "-di", "-w", Long.toString(ProcessHandle.current().pid())));
    }

    @Override
    public synchronized boolean start() {
        if (isRunning()) {
            return true;
        }
        try {
            process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (IOException e) {
            process = null;
            return false;
        }
    }

    @Override
    public synchronized void stop() {
        if (process == null) {
            return;
        }
        Process p = process;
        process = null;
        p.destroy();
        try {
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }
}
