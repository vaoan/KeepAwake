package com.keepawake;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Programmatic 22x22 tray icons, legible on light and dark menu bars. */
final class Icons {
    private static final int SIZE = 22;

    private Icons() {}

    static Image off() {
        return draw(null, new Color(150, 150, 150));
    }

    static Image on() {
        return draw(new Color(52, 199, 89), new Color(20, 120, 50));
    }

    static Image warning() {
        return draw(new Color(255, 176, 20), new Color(160, 100, 0));
    }

    private static Image draw(Color fill, Color outline) {
        BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
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
