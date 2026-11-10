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
    private final Models models = new Models();
    private BufferedImage lastIcon;
    private String lastTip;
    private volatile boolean alive = true;
    private volatile boolean closed;

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
        String text = snapshot(model, 0);
        if (text.equals(lastMenu)) return;
        lastMenu = text;
        send(snapshot(model, models.add(MenuModel.flatten(model))));
    }

    // ---- protocol (pure) ----

    /**
     * "menu GEN N", then N lines in pre-order: "item tag enabled checked label" (checked -1 none, 0, 1), "sep",
     * "sub label" followed by its children and "endsub". Tags are indices of {@link MenuModel#flatten}.
     */
    static String snapshot(List<MenuModel.Entry> model, int generation) {
        StringBuilder body = new StringBuilder();
        int lines = write(model, 0, body);
        return "menu\t" + generation + "\t" + lines + "\n" + body;
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

    /** The runnable entry for a tag in the model last sent; null for a bad tag, separator, submenu or disabled row. */
    static MenuModel.Entry lookup(List<MenuModel.Entry> flat, int tag) {
        if (tag < 0 || tag >= flat.size()) return null;
        MenuModel.Entry e = flat.get(tag);
        if (e.isSeparator() || e.isSubmenu() || !e.enabled() || e.action() == null) return null;
        return e;
    }

    /** {generation, tag} of a "click gen tag" line, or null. */
    static int[] parseClick(String line) {
        String[] p = line.split("\t");
        if (p.length != 3 || !p[0].equals("click")) return null;
        try {
            return new int[] {Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The menus Java has sent, by generation. A click carries the generation of the row the person saw, and runs that
     * generation's action or nothing. Keeps the one the helper reports showing (it may hold a newer menu back while
     * its menu is open) and the latest; a click on a generation drops the older ones. EDT only.
     */
    static final class Models {
        private final java.util.Map<Integer, List<MenuModel.Entry>> byGeneration = new java.util.HashMap<>();
        private int latest;
        private int shown;

        int add(List<MenuModel.Entry> flat) {
            int generation = ++latest;
            byGeneration.put(generation, flat);
            byGeneration.keySet().removeIf(g -> g != generation && g != shown);
            return generation;
        }

        /** The helper now displays this generation: older ones can no longer be clicked. */
        void shown(int generation) {
            shown = Math.max(shown, generation);
            byGeneration.keySet().removeIf(g -> g < shown);
        }

        MenuModel.Entry resolve(int generation, int tag) {
            List<MenuModel.Entry> flat = byGeneration.get(generation);
            MenuModel.Entry e = flat == null ? null : lookup(flat, tag);
            if (e != null) shown(generation);
            return e;
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
                } else if (line.startsWith("shown\t")) {
                    try {
                        int generation = Integer.parseInt(line.substring(6).trim());
                        EventQueue.invokeLater(() -> models.shown(generation));
                    } catch (NumberFormatException ignored) {
                        // Not a generation.
                    }
                } else if (line.startsWith("click\t")) {
                    int[] click = parseClick(line);
                    if (click == null) continue;
                    EventQueue.invokeLater(() -> {
                        MenuModel.Entry e = models.resolve(click[0], click[1]);
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
