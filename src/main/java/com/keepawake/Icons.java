package com.keepawake;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Programmatic tray icons, legible on light and dark menu bars. 22x22 for the AWT TrayIcon; the same drawing at
 * 44x44 for the native helper (shown at 22 pt, 2x). Images are cached: callers can compare them by identity.
 */
final class Icons {
    private static final int SIZE = 22;
    private static final Color OFF_OUTLINE = new Color(150, 150, 150);
    private static final Color ON_FILL = new Color(52, 199, 89);
    private static final Color ON_OUTLINE = new Color(20, 120, 50);
    private static final Color WARN_FILL = new Color(255, 176, 20);
    private static final Color WARN_OUTLINE = new Color(160, 100, 0);

    private static final BufferedImage OFF = draw(null, OFF_OUTLINE, 1);
    private static final BufferedImage ON = draw(ON_FILL, ON_OUTLINE, 1);
    private static final BufferedImage WARNING = draw(WARN_FILL, WARN_OUTLINE, 1);
    private static final BufferedImage OFF_2X = draw(null, OFF_OUTLINE, 2);
    private static final BufferedImage ON_2X = draw(ON_FILL, ON_OUTLINE, 2);
    private static final BufferedImage WARNING_2X = draw(WARN_FILL, WARN_OUTLINE, 2);

    private Icons() {}

    static BufferedImage off() {
        return OFF;
    }

    static BufferedImage on() {
        return ON;
    }

    static BufferedImage warning() {
        return WARNING;
    }

    /** 44x44 for the native helper. */
    static BufferedImage off2x() {
        return OFF_2X;
    }

    static BufferedImage on2x() {
        return ON_2X;
    }

    static BufferedImage warning2x() {
        return WARNING_2X;
    }

    private static BufferedImage draw(Color fill, Color outline, int scale) {
        BufferedImage img = new BufferedImage(SIZE * scale, SIZE * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(scale, scale);
            if (fill != null) {
                g.setColor(fill);
                g.fillOval(3, 3, SIZE - 6, SIZE - 6);
            }
            g.setColor(outline);
            g.setStroke(new BasicStroke(2f));
            g.drawOval(3, 3, SIZE - 7, SIZE - 7);
        } finally {
            g.dispose();
        }
        return img;
    }
}
