package org.trostheide.svgtoolbox.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatLightLaf;

public class GuiRunner {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Configure FlatLaf styling before setup
                UIManager.put("Button.arc", 6);
                UIManager.put("Component.arc", 6);
                UIManager.put("TextComponent.arc", 6);
                UIManager.put("ScrollBar.width", 10);
                UIManager.put("ScrollBar.trackArc", 999);
                UIManager.put("ScrollBar.thumbArc", 999);
                UIManager.put("ScrollPane.smoothScrolling", true);
                UIManager.put("TitlePane.unifiedBackground", true);

                FlatLightLaf.setup();
            } catch (Exception ignored) {
            }
            new MainWindow().setVisible(true);
        });
    }
}
