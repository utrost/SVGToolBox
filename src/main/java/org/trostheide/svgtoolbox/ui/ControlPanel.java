package org.trostheide.svgtoolbox.ui;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.SvgToolboxRunner;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

public class ControlPanel extends JPanel {

    private JCheckBox chkEnableHatching;
    private JCheckBox chkOptimize;
    private JComboBox<String> cmbPattern;
    private JSlider sldStrokeWidth;
    private MainWindow parent;

    private File currentInputFile;

    private JButton btnProcess;
    private JButton btnSave;
    private JButton btnLoad;

    public ControlPanel(MainWindow parent) {
        this.parent = parent;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(450, 0));
        add(Box.createVerticalStrut(10));

        add(createGlobalSettings());
        add(createGeometrySettings());
        
        // Use a scroll pane for layers since there could be many
        JScrollPane scroll = new JScrollPane(createLayerSettings());
        scroll.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        scroll.setPreferredSize(new Dimension(450, 250));
        add(scroll);

        add(Box.createVerticalGlue());

        btnProcess = new JButton("Update Preview");
        btnProcess.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnProcess.addActionListener(e -> updatePreview());
        add(btnProcess);

        JPanel pnlButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnLoad = new JButton("Load SVG...");
        btnLoad.addActionListener(e -> loadSvg());
        btnSave = new JButton("Save As...");
        btnSave.addActionListener(e -> saveOutput());
        pnlButtons.add(btnLoad);
        pnlButtons.add(btnSave);
        
        add(Box.createVerticalStrut(10));
        add(pnlButtons);

        add(Box.createVerticalStrut(20));
    }

    private void setControlsEnabled(boolean b) {
        btnProcess.setEnabled(b);
        btnSave.setEnabled(b);
        btnProcess.setText(b ? "Update Preview" : "Processing...");
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

        JPanel pnlWidth = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel lblWidthDesc = new JLabel("Stroke Width (px): 1.0");
        pnlWidth.add(lblWidthDesc);
        p.add(pnlWidth);

        sldStrokeWidth = new JSlider(0, 50, 10); // 0-5.0 px
        sldStrokeWidth.addChangeListener(e -> {
            lblWidthDesc.setText(String.format("Stroke Width (px): %.1f", sldStrokeWidth.getValue() / 10f));
        });
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

    private void loadSvg() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SVG Files (*.svg)", "svg"));
        if (currentInputFile != null) {
            fc.setCurrentDirectory(currentInputFile.getParentFile());
        }
        
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = fc.getSelectedFile();
            parent.setInputFile(selected); // This will implicitly call this.setInputFile()
        }
    }

    public void setInputFile(File f) {
        this.currentInputFile = f;
        refreshLayerSettings();
        updatePreview();
    }

    private JPanel layerSettingsPanel;
    
    // UI elements per layer: ColorHex -> LayerWidgets
    private java.util.Map<String, LayerWidgets> layerWidgets = new java.util.HashMap<>();
    
    private static class LayerWidgets {
        JCheckBox chkExport;
        JComboBox<String> cmbPattern;
        JSlider sldStrokeWidth;
    }

    private JPanel createLayerSettings() {
        layerSettingsPanel = new JPanel();
        layerSettingsPanel.setLayout(new BoxLayout(layerSettingsPanel, BoxLayout.Y_AXIS));
        layerSettingsPanel.setBorder(new TitledBorder("Layer Overrides"));
        return layerSettingsPanel;
    }

    private void refreshLayerSettings() {
        layerSettingsPanel.removeAll();
        layerWidgets.clear();

        if (currentInputFile == null) return;

        try {
            java.util.Set<String> colors = org.trostheide.svgtoolbox.core.SvgAnalyzer.extractLayerColors(currentInputFile);
            
            if (colors.isEmpty()) {
                layerSettingsPanel.add(new JLabel("No colored layers found."));
            } else {
                for (String colorHex : colors) {
                    JPanel row = new JPanel();
                    row.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));

                    // Color block
                    JPanel colorBox = new JPanel();
                    colorBox.setPreferredSize(new Dimension(16, 16));
                    try {
                        colorBox.setBackground(Color.decode(colorHex));
                    } catch (Exception e) {}
                    row.add(colorBox);

                    // Name
                    row.add(new JLabel(colorHex));

                    // Export Checkbox
                    JCheckBox chkExport = new JCheckBox("Exp", true);
                    row.add(chkExport);

                    // Hatch Pattern
                    JComboBox<String> cmbPat = new JComboBox<>(new String[] { "Global", "none", "linear", "cross", "zigzag", "wave", "dot" });
                    row.add(cmbPat);

                    // Stroke Width
                    JSlider sldWidth = new JSlider(0, 50, 0); // 0 means use global
                    sldWidth.setPreferredSize(new Dimension(80, 20));
                    sldWidth.setToolTipText("0 = Use Global");

                    JLabel lblVal = new JLabel("Global");
                    lblVal.setPreferredSize(new Dimension(45, 20));
                    sldWidth.addChangeListener(e -> {
                        int v = sldWidth.getValue();
                        lblVal.setText(v == 0 ? "Global" : String.format("%.1fpx", v / 10f));
                    });

                    row.add(sldWidth);
                    row.add(lblVal);

                    // Store widgets
                    LayerWidgets widgets = new LayerWidgets();
                    widgets.chkExport = chkExport;
                    widgets.cmbPattern = cmbPat;
                    widgets.sldStrokeWidth = sldWidth;
                    layerWidgets.put(colorHex, widgets);

                    layerSettingsPanel.add(row);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting layers: " + e.getMessage());
            layerSettingsPanel.add(new JLabel("Error loading layers"));
        }

        layerSettingsPanel.revalidate();
        layerSettingsPanel.repaint();
    }

    private Config buildConfigFromGui(File tempOut) {
        String cropVal = (String) cmbCrop.getSelectedItem();
        java.awt.geom.Rectangle2D cropRect = null;

        if ("Current View".equals(cropVal)) {
            cropRect = parent.getPreviewPanel().getViewportBounds();
        } else if (!"None".equals(cropVal)) {
            cropRect = SvgToolboxRunner.parseCrop(cropVal);
        }

        java.util.Map<String, org.trostheide.svgtoolbox.HatchStyle> styleOverrides = new java.util.HashMap<>();
        java.util.Map<String, Float> strokeWidthOverrides = new java.util.HashMap<>();
        java.util.List<String> hiddenLayers = new java.util.ArrayList<>();
        java.util.List<Color> noHatchColors = new java.util.ArrayList<>();

        for (java.util.Map.Entry<String, LayerWidgets> entry : layerWidgets.entrySet()) {
            String hex = entry.getKey();
            LayerWidgets lw = entry.getValue();

            if (!lw.chkExport.isSelected()) {
                hiddenLayers.add(hex);
                continue; // no need to build the rest for hidden
            }

            float width = lw.sldStrokeWidth.getValue() / 10f;
            if (width > 0) {
                strokeWidthOverrides.put(hex, width);
            }

            String pat = (String) lw.cmbPattern.getSelectedItem();
            if ("none".equals(pat)) {
                try {
                    noHatchColors.add(Color.decode(hex));
                } catch(Exception ignored) {}
            } else if (!"Global".equals(pat)) {
                // To properly support overriding the hatch *type*, we must pass it to Config.
                // Currently Config only has a global hatchPattern string and HatchStyle(angle, gap, cross) overrides.
                // For now, if "cross", we can intercept it, else we might not fully hook up the zigzag/wave per color.
                // Wait, Config doesn't support per-color Hatch Pattern string yet, only crossHatch boolean.
                // Let's assume global overrides crossHatch. If they selected cross, we set cross to true.
                boolean isCross = "cross".equals(pat);
                styleOverrides.put(hex, new org.trostheide.svgtoolbox.HatchStyle(45.0, 5.0, isCross));
            }
        }

        return new Config.Builder()
                .inputPath(currentInputFile.getAbsolutePath())
                .outputPath(tempOut.getAbsolutePath())
                .strokeWidth(sldStrokeWidth.getValue() / 10f)
                .enableHatching(chkEnableHatching.isSelected())
                .hatchPattern((String) cmbPattern.getSelectedItem())
                .rotationDegrees(currentRotation)
                .cropBounds(cropRect)
                .optimizePaths(chkOptimize.isSelected())
                .hiddenLayers(hiddenLayers)
                .strokeWidthOverrides(strokeWidthOverrides)
                .overrides(styleOverrides)
                .noHatchColors(noHatchColors)
                .build();
    }

    private void updatePreview() {
        if (currentInputFile == null)
            return;

        try {
            final File tempOut = File.createTempFile("preview_", ".svg");
            final Config config = buildConfigFromGui(tempOut);

            setControlsEnabled(false);

            SwingWorker<Void, Void> worker = new SwingWorker<>() {
                @Override
                protected Void doInBackground() throws Exception {
                    SvgToolboxRunner.processPipeline(config);
                    return null;
                }

                @Override
                protected void done() {
                    setControlsEnabled(true);
                    try {
                        get(); // throw exception if any occurred
                        parent.getPreviewPanel().loadFile(tempOut);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(ControlPanel.this, "Error processing SVG: " + ex.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error setup: " + e.getMessage());
        }
    }

    // Call this from a new Save button
    private void saveOutput() {
        if (currentInputFile == null)
            return;

        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                final Config config = buildConfigFromGui(fc.getSelectedFile());

                setControlsEnabled(false);

                SwingWorker<Void, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        SvgToolboxRunner.processPipeline(config);
                        return null;
                    }

                    @Override
                    protected void done() {
                        setControlsEnabled(true);
                        try {
                            get(); // throw exception if any occurred
                            JOptionPane.showMessageDialog(ControlPanel.this, "Saved to " + fc.getSelectedFile().getName());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            JOptionPane.showMessageDialog(ControlPanel.this, "Error saving SVG: " + ex.getCause().getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
                worker.execute();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error setup: " + e.getMessage());
            }
        }
    }
}
