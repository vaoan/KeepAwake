package com.keepawake;

import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * The menu-bar item drawn by the native helper (native/KeepAwakeBar.swift, built by native/build-bar.sh); same design
 * as unichat's. Left click toggles, right click opens the menu. The menu is the {@link MenuModel} tree: it is sent
 * flattened, and a click carries the tag (index) of the entry in the model last sent. If the helper is missing or
 * dies the caller falls back to the AWT TrayIcon.
 */
final class NativeBar {

    private final Process process;
    private final Writer out;
    private String lastMenu = "";
    private volatile List<MenuModel.Entry> lastModel = List.of();
    private BufferedImage lastIcon;
    private String lastTip;
    private volatile boolean alive = true;
    private volatile boolean closed;
    private volatile Boolean notificationsAllowed;

    private NativeBar(Process process) {
        this.process = process;
        this.out = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
    }

    /**
     * Starts the helper; null if it is not there or cannot run. {@code onToggle} and {@code afterAction} run on the
     * EDT (afterAction after a menu action). {@code onExit} runs once, on the reader thread, if it dies by itself.
     * The reader is a non-daemon thread: with no window and no TrayIcon it is what keeps the JVM running.
     */
    static NativeBar start(Path helper, Runnable onToggle, Runnable afterAction, Runnable onExit) {
        if (helper == null || !Files.isExecutable(helper)) return null;
        try {
            NativeBar bar = new NativeBar(new ProcessBuilder(helper.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start());
            Thread reader = new Thread(() -> bar.readClicks(onToggle, afterAction, onExit), "native-bar");
            reader.setDaemon(false);
            reader.start();
            return bar;
        } catch (IOException e) {
            return null;
        }
    }

    /** Removes the item (the app is quitting): the helper exits when its input closes. */
    void close() {
        closed = true;
        alive = false;
        try {
            out.close();
        } catch (IOException ignored) {
            // Already gone.
        }
        process.destroy();
    }

    boolean isAlive() {
        return alive;
    }

    /** Whether macOS lets the helper post notifications: null until it says, then true or false. */
    Boolean notificationsAllowed() {
        return notificationsAllowed;
    }

    void notify(String title, String body) {
        send(notifyLine(title, body));
    }

    /** The image is 44x44 (shown at 22 pt). Images are cached, so the same object means the same picture. */
    void setIcon(BufferedImage image) {
        if (image == lastIcon) return;
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", png);
        } catch (IOException e) {
            return;
        }
        lastIcon = image;
        send("icon\t" + Base64.getEncoder().encodeToString(png.toByteArray()) + "\n");
    }

    void setTooltip(String text) {
        String line = tipLine(text);
        if (line.equals(lastTip)) return;
        lastTip = line;
        send(line);
    }

    /** Sends the menu if anything changed since the last time, and remembers it for clicks. Call on the EDT. */
    void syncMenu(List<MenuModel.Entry> model) {
        String snapshot = snapshot(model);
        if (snapshot.equals(lastMenu)) return;
        lastMenu = snapshot;
        lastModel = MenuModel.flatten(model);
        send(snapshot);
    }

    // ---- protocol (pure) ----

    /**
     * "menu N", then N lines in pre-order: "item tag enabled checked label" (checked -1 none, 0, 1), "sep",
     * "sub label" followed by its children and "endsub". Tags are indices of {@link MenuModel#flatten}.
     */
    static String snapshot(List<MenuModel.Entry> model) {
        StringBuilder body = new StringBuilder();
        int lines = write(model, 0, body);
        return "menu\t" + lines + "\n" + body;
    }

    private static int write(List<MenuModel.Entry> entries, int firstTag, StringBuilder sb) {
        int tag = firstTag;
        int lines = 0;
        for (MenuModel.Entry e : entries) {
            int mine = tag++;
            if (e.isSeparator()) {
                sb.append("sep\n");
                lines++;
            } else if (e.isSubmenu()) {
                sb.append("sub\t").append(clean(e.label())).append('\n');
                int inner = write(e.children(), tag, sb);
                tag += MenuModel.flatten(e.children()).size();
                sb.append("endsub\n");
                lines += inner + 2;
            } else {
                int checked = e.checked() == null ? -1 : (e.checked() ? 1 : 0);
                sb.append("item\t").append(mine).append('\t').append(e.enabled() ? 1 : 0).append('\t')
                        .append(checked).append('\t').append(clean(e.label())).append('\n');
                lines++;
            }
        }
        return lines;
    }

    static String tipLine(String text) {
        return "tip\t" + clean(text) + "\n";
    }

    static String notifyLine(String title, String body) {
        return "notify\t" + clean(title) + "\t" + clean(body) + "\n";
    }

    /** The runnable entry for a tag in the model last sent; null for a bad tag, separator, submenu or disabled row. */
    static MenuModel.Entry lookup(List<MenuModel.Entry> flat, int tag) {
        if (tag < 0 || tag >= flat.size()) return null;
        MenuModel.Entry e = flat.get(tag);
        if (e.isSeparator() || e.isSubmenu() || !e.enabled() || e.action() == null) return null;
        return e;
    }

    /** The tag of a "click" line, or -1. */
    static int parseClick(String line) {
        if (!line.startsWith("click\t")) return -1;
        try {
            return Integer.parseInt(line.substring(6).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    // ---- io ----

    private void readClicks(Runnable onToggle, Runnable afterAction, Runnable onExit) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals("toggle")) {
                    EventQueue.invokeLater(onToggle);
                } else if (line.startsWith("notify-auth\t")) {
                    notificationsAllowed = line.endsWith("1");
                } else if (line.startsWith("click\t")) {
                    int tag = parseClick(line);
                    EventQueue.invokeLater(() -> {
                        MenuModel.Entry e = lookup(lastModel, tag);
                        if (e == null) return;
                        e.action().run();
                        afterAction.run();
                    });
                }
            }
        } catch (IOException ignored) {
            // Treated like EOF below.
        }
        alive = false;
        if (!closed) onExit.run();
    }

    private synchronized void send(String lines) {
        if (!alive) return;
        try {
            out.write(lines);
            out.flush();
        } catch (IOException e) {
            alive = false; // the reader thread sees EOF and runs onExit
            process.destroy();
        }
    }
}
