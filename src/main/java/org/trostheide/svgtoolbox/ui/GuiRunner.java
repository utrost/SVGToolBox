package org.trostheide.svgtoolbox.ui;

import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLightLaf;

public class GuiRunner {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Initialize modern Material Design FlatLaf look and feel
                FlatLightLaf.setup();
            } catch (Exception ignored) {
            }
            new MainWindow().setVisible(true);
        });
    }
}
