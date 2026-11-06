package com.keepawake;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/** Reads Claude Code session files from a directory and reports the live interactive ones. */
public class SessionScanner {
    private static final Pattern FILE = Pattern.compile("^(\\d+)\\.json$");
    private static final long PID_REUSE_TOLERANCE_MILLIS = 5 * 60 * 1000L;

    private final Path dir;
    private final ProcessProbe probe;

    public SessionScanner(Path dir, ProcessProbe probe) {
        this.dir = dir;
        this.probe = probe;
    }

    public Snapshot scan() {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (FILE.matcher(p.getFileName().toString()).matches()) files.add(p);
            }
        } catch (IOException | RuntimeException e) {
            return new Snapshot(List.of(), false);
        }

        List<Session> sessions = new ArrayList<>();
        for (Path file : files) {
            Session s = read(file);
            if (s != null) sessions.add(s);
        }
        return new Snapshot(sessions, true);
    }

    /** Returns the session, or null if it should not count. */
    private Session read(Path file) {
        long pid;
        try {
            var m = FILE.matcher(file.getFileName().toString());
            if (!m.matches()) return null;
            pid = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }

        JsonObject obj = parse(file);
        if (obj == null) {
            // Corrupt: keep the machine awake if the process is alive.
            if (!probe.isAlive(pid, OptionalLong.empty())) return null;
            return new Session(pid, Long.toString(pid), SessionStatus.UNKNOWN);
        }

        String kind = string(obj, "kind");
        if (kind != null && !kind.equals("interactive")) return null;

        OptionalLong startedAt = OptionalLong.empty();
        JsonElement sa = obj.get("startedAt");
        if (sa != null && sa.isJsonPrimitive() && sa.getAsJsonPrimitive().isNumber()) {
            try {
                startedAt = OptionalLong.of(sa.getAsLong());
            } catch (NumberFormatException ignored) {
            }
        }
        if (!probe.isAlive(pid, startedAt)) return null;

        String name = string(obj, "name");
        if (name == null) name = Long.toString(pid);
        return new Session(pid, name, status(string(obj, "status")));
    }

    private static JsonObject parse(Path file) {
        try {
            JsonElement el = JsonParser.parseString(Files.readString(file));
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return null;
        return e.getAsString();
    }

    private static SessionStatus status(String s) {
        if ("busy".equals(s)) return SessionStatus.WORKING;
        if ("idle".equals(s)) return SessionStatus.WAITING;
        return SessionStatus.UNKNOWN;
    }

    public static ProcessProbe systemProbe() {
        return (pid, startedAt) -> {
            var handle = ProcessHandle.of(pid);
            if (handle.isEmpty() || !handle.get().isAlive()) return false;
            if (startedAt.isPresent()) {
                Instant start = handle.get().info().startInstant().orElse(null);
                if (start != null
                        && Math.abs(start.toEpochMilli() - startedAt.getAsLong()) > PID_REUSE_TOLERANCE_MILLIS) {
                    return false;
                }
            }
            return true;
        };
    }
}
