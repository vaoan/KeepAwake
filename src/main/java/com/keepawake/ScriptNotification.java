package com.keepawake;

import java.io.IOException;

/** A notification through macOS's script runner, for when the helper's own notifications are denied or it is missing. */
final class ScriptNotification {
    private ScriptNotification() {}

    /** AppleScript source; quotes and backslashes are escaped, line breaks become spaces. */
    static String script(String title, String body) {
        return "display notification \"" + escape(body) + "\" with title \"" + escape(title) + "\"";
    }

    private static String escape(String s) {
        return (s == null ? "" : s).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace('\n', ' ').replace('\r', ' ');
    }

    /** Runs osascript off the calling thread; any failure is ignored. */
    static void post(String title, String body) {
        Thread t = new Thread(() -> {
            try {
                Process p = new ProcessBuilder("osascript", "-e", script(title, body))
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                p.waitFor();
            } catch (IOException e) {
                // Ignored: a missing notification is not worth an error.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "script-notification");
        t.setDaemon(true);
        t.start();
    }
}
