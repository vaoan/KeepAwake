package com.keepawake;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * The menu's two options, kept across restarts in a properties file. Both default to on (follow Claude): a missing or
 * unreadable file, or a missing key, means on.
 */
record Settings(boolean autoOn, boolean autoOff) {
    static final Settings DEFAULT = new Settings(true, true);
    private static final String AUTO_ON = "autoOn";
    private static final String AUTO_OFF = "autoOff";

    static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), "Library", "Application Support", "KeepAwake",
                "settings.properties");
    }

    static Settings of(Controller controller) {
        return new Settings(controller.isAutoOn(), controller.isAutoOff());
    }

    static Settings load(Path file) {
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(in);
        } catch (IOException | IllegalArgumentException e) {
            return DEFAULT;
        }
        return new Settings(flag(p, AUTO_ON, DEFAULT.autoOn), flag(p, AUTO_OFF, DEFAULT.autoOff));
    }

    private static boolean flag(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v.trim());
    }

    /** Writes a temporary file and moves it into place, so a crash never leaves half a file. */
    void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty(AUTO_ON, Boolean.toString(autoOn));
        p.setProperty(AUTO_OFF, Boolean.toString(autoOff));
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            p.store(out, "KeepAwake");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Turns the saved options on in {@code controller}; auto-on may turn it on at once if Claude is working. */
    void apply(Controller controller, java.time.Instant now) {
        controller.setAutoOff(autoOff, now);
        controller.setAutoOn(autoOn, now);
    }
}
