package org.trostheide.svgtoolbox.ui;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;
import org.trostheide.svgtoolbox.SvgToolboxRunner;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.ArrayList;

public class ControlPanel extends JPanel {

    private JCheckBox chkEnableHatching;
    private JCheckBox chkOptimize;
    private JComboBox<String> cmbPattern;
    private JSlider sldStrokeWidth;
    private MainWindow parent;

    private File currentInputFile;

    public ControlPanel(MainWindow parent) {
        this.parent = parent;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(300, 0));

        add(createGlobalSettings());
        add(createGeometrySettings());
        add(Box.createVerticalGlue());

        JButton btnProcess = new JButton("Update Preview");
        btnProcess.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnProcess.addActionListener(e -> updatePreview());
        add(btnProcess);

        JButton btnSave = new JButton("Save As...");
        btnSave.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnSave.addActionListener(e -> saveOutput());
        add(Box.createVerticalStrut(10));
        add(btnSave);

        add(Box.createVerticalStrut(20));
    }

    private JPanel createGlobalSettings() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new TitledBorder("Global Settings"));

        chkEnableHatching = new JCheckBox("Enable Hatching", true);
        p.add(chkEnableHatching);

        chkOptimize = new JCheckBox("Optimize Path Travel", false);
        p.add(chkOptimize);

        p.add(new JLabel("Pattern:"));
        cmbPattern = new JComboBox<>(new String[] { "linear", "cross", "zigzag", "wave", "dot" });
        p.add(cmbPattern);

        p.add(new JLabel("Stroke Width (px):"));
        sldStrokeWidth = new JSlider(0, 50, 10); // 0-5.0 px
        p.add(sldStrokeWidth);

        return p;
    }

    private JComboBox<String> cmbCrop;
    private JButton btnRotate90;
    private double currentRotation = 0;

    private JPanel createGeometrySettings() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new TitledBorder("Layout & Geometry"));

        JPanel pRotate = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pRotate.add(new JLabel("Rotate:"));
        btnRotate90 = new JButton("90°");
        btnRotate90.addActionListener(e -> {
            currentRotation = (currentRotation + 90) % 360;
            updatePreview();
        });
        pRotate.add(btnRotate90);
        p.add(pRotate);

        p.add(new JLabel("Crop to:"));
        cmbCrop = new JComboBox<>(new String[] { "None", "Current View", "A4", "Letter", "500x500" });
        p.add(cmbCrop);

        return p;
    }

    public void setInputFile(File f) {
        this.currentInputFile = f;
        updatePreview();
    }

    private void updatePreview() {
        if (currentInputFile == null)
            return;

        try {
            // Create a temp file for output
            File tempOut = File.createTempFile("preview_", ".svg");

            String cropVal = (String) cmbCrop.getSelectedItem();
            java.awt.geom.Rectangle2D cropRect = null;

            if ("Current View".equals(cropVal)) {
                cropRect = parent.getPreviewPanel().getViewportBounds();
            } else if (!"None".equals(cropVal)) {
                cropRect = SvgToolboxRunner.parseCrop(cropVal);
            }

            // Build Config from GUI
            Config config = new Config(
                    currentInputFile.getAbsolutePath(),
                    tempOut.getAbsolutePath(),
                    sldStrokeWidth.getValue() / 10f,
                    new ArrayList<>(), // Palette
                    chkEnableHatching.isSelected(),
                    new HatchStyle(45, 5, false), // Default style for now
                    Collections.emptyMap(),
                    Collections.emptyList(),
                    100.0, // minArea
                    0.5, // simplify
                    (String) cmbPattern.getSelectedItem(),
                    currentRotation, // Rotate
                    false, // Stats
                    cropRect, // Crop
                    chkOptimize.isSelected() // Optimize
            );

            SvgToolboxRunner.processPipeline(config);

            // Update preview
            parent.getPreviewPanel().loadFile(tempOut);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    // Call this from a new Save button
    private void saveOutput() {
        if (currentInputFile == null)
            return;

        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                // Re-run pipeline to the chosen output file
                String cropVal = (String) cmbCrop.getSelectedItem();
                java.awt.geom.Rectangle2D cropRect = null;

                if ("Current View".equals(cropVal)) {
                    cropRect = parent.getPreviewPanel().getViewportBounds();
                } else if (!"None".equals(cropVal)) {
                    cropRect = SvgToolboxRunner.parseCrop(cropVal);
                }

                Config config = new Config(
                        currentInputFile.getAbsolutePath(),
                        fc.getSelectedFile().getAbsolutePath(),
                        sldStrokeWidth.getValue() / 10f,
                        new ArrayList<>(),
                        chkEnableHatching.isSelected(),
                        new HatchStyle(45, 5, false),
                        Collections.emptyMap(),
                        Collections.emptyList(),
                        100.0,
                        0.5,
                        (String) cmbPattern.getSelectedItem(),
                        currentRotation,
                        false,
                        cropRect,
                        chkOptimize.isSelected()); // Optimize
                SvgToolboxRunner.processPipeline(config);
                JOptionPane.showMessageDialog(this, "Saved to " + fc.getSelectedFile().getName());
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error saving: " + e.getMessage());
            }
        }
    }
}
