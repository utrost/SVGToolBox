package org.trostheide.svgtoolbox.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class GuiRunner {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new MainWindow().setVisible(true);
        });
    }
}
