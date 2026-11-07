package com.keepawake;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Programmatic tray icons, a coffee cup legible on light and dark menu bars: outline when off, full and steaming when
 * on. 22x22 for the AWT TrayIcon; the same drawing at
 * 44x44 for the native helper (shown at 22 pt, 2x). Images are cached: callers can compare them by identity.
 */
final class Icons {
    private static final int SIZE = 22;
    private static final Color OFF_OUTLINE = new Color(150, 150, 150);
    private static final Color ON_FILL = new Color(255, 149, 0);
    private static final Color ON_OUTLINE = new Color(170, 90, 0);
    private static final Color WARN_FILL = new Color(255, 69, 58);
    private static final Color WARN_OUTLINE = new Color(170, 30, 25);

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
            Path2D cup = new Path2D.Float();
            cup.moveTo(4.5, 9.5);
            cup.lineTo(14.5, 9.5);
            cup.lineTo(14.5, 14);
            cup.quadTo(14.5, 18.5, 11, 18.5);
            cup.lineTo(8, 18.5);
            cup.quadTo(4.5, 18.5, 4.5, 14);
            cup.closePath();
            if (fill != null) {
                g.setColor(fill);
                g.fill(cup);
            }
            g.setColor(outline);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(cup);
            g.draw(new Arc2D.Double(12, 10.5, 6, 5, -90, 180, Arc2D.OPEN));
            if (fill != null) {
                g.setColor(fill);
                drawSteam(g, 7.5f);
                drawSteam(g, 11.5f);
            }
        } finally {
            g.dispose();
        }
        return img;
    }

    private static void drawSteam(Graphics2D g, float x) {
        Path2D steam = new Path2D.Float();
        steam.moveTo(x, 7);
        steam.curveTo(x - 1.5f, 5.5f, x + 1.5f, 4, x, 2.5f);
        g.draw(steam);
    }
}
