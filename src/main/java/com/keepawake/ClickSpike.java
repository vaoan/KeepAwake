package com.keepawake;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** Throwaway spike: how does a TrayIcon without a PopupMenu report clicks on macOS? */
public class ClickSpike {
    public static void main(String[] args) throws AWTException {
        System.setProperty("apple.awt.UIElement", "true");
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray not supported");
            System.exit(1);
        }
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillOval(2, 2, 28, 28);
        g.setColor(Color.BLACK);
        g.drawOval(2, 2, 28, 28);
        g.dispose();

        TrayIcon icon = new TrayIcon(img, "ClickSpike");
        icon.setImageAutoSize(true);
        icon.addMouseListener(new MouseAdapter() {
            private void log(String type, MouseEvent e) {
                System.out.println(type + " button=" + e.getButton()
                        + " popupTrigger=" + e.isPopupTrigger()
                        + " clickCount=" + e.getClickCount()
                        + " modifiersEx=" + MouseEvent.getModifiersExText(e.getModifiersEx()));
                System.out.flush();
            }
            @Override public void mousePressed(MouseEvent e) { log("pressed", e); }
            @Override public void mouseReleased(MouseEvent e) { log("released", e); }
            @Override public void mouseClicked(MouseEvent e) { log("clicked", e); }
        });
        SystemTray.getSystemTray().add(icon);
        System.out.println("ClickSpike ready; click the tray icon");
        System.out.flush();
    }
}
