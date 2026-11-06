package com.keepawake;

import java.awt.SystemTray;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.setProperty("apple.awt.UIElement", "true");
        boolean helper = System.getProperty("keepawake.bar") != null;
        if (!helper && !SystemTray.isSupported()) {
            JOptionPane.showMessageDialog(null, "Esta plataforma no admite icono en la barra de menús");
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                TrayApp app = new TrayApp();
                Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
                app.start();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "No se pudo iniciar KeepAwake: " + e.getMessage());
                System.exit(1);
            }
        });
    }
}
