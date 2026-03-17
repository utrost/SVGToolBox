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
        java.awt.FileDialog fd = new java.awt.FileDialog(parent, "Load SVG...", java.awt.FileDialog.LOAD);
        fd.setFile("*.svg"); // Generic filter hint
        if (currentInputFile != null) {
            fd.setDirectory(currentInputFile.getParent());
        }
        
        fd.setVisible(true);
        if (fd.getFile() != null) {
            File selected = new File(fd.getDirectory(), fd.getFile());
            parent.setInputFile(selected); // This will implicitly call this.setInputFile()
        }
    }

    public void setInputFile(File f) {
        this.currentInputFile = f;
        refreshLayerSettings();
        updatePreview();
    }

    private JPanel layerSettingsPanel;
    
    // UI elements per layer: layerId -> LayerWidgets
    private java.util.Map<String, LayerWidgets> layerWidgets = new java.util.LinkedHashMap<>();
    // Map layerId -> LayerInfo for resolving colors in buildConfigFromGui
    private java.util.Map<String, org.trostheide.svgtoolbox.core.SvgAnalyzer.LayerInfo> layerInfoMap = new java.util.LinkedHashMap<>();
    
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
        layerInfoMap.clear();

        if (currentInputFile == null) return;

        try {
            java.util.List<org.trostheide.svgtoolbox.core.SvgAnalyzer.LayerInfo> layers =
                org.trostheide.svgtoolbox.core.SvgAnalyzer.extractLayers(currentInputFile);
            
            if (layers.isEmpty()) {
                layerSettingsPanel.add(new JLabel("No layers found."));
            } else {
                for (org.trostheide.svgtoolbox.core.SvgAnalyzer.LayerInfo layer : layers) {
                    String colorHex = layer.primaryColor;

                    JPanel row = new JPanel();
                    row.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));

                    // Color swatch(es)
                    JPanel colorBox = new JPanel();
                    colorBox.setPreferredSize(new Dimension(16, 16));
                    try {
                        colorBox.setBackground(Color.decode(colorHex));
                    } catch (Exception e) {}
                    row.add(colorBox);

                    // Layer name
                    String tooltip = layer.id + " — colors: " + String.join(", ", layer.colors);
                    JLabel lblName = new JLabel(layer.label);
                    lblName.setToolTipText(tooltip);
                    lblName.setPreferredSize(new Dimension(120, 20));
                    row.add(lblName);

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

                    // Store widgets and layer info keyed by layer id
                    LayerWidgets widgets = new LayerWidgets();
                    widgets.chkExport = chkExport;
                    widgets.cmbPattern = cmbPat;
                    widgets.sldStrokeWidth = sldWidth;
                    layerWidgets.put(layer.id, widgets);
                    layerInfoMap.put(layer.id, layer);

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
            String layerId = entry.getKey();
            LayerWidgets lw = entry.getValue();

            // Resolve layer id back to color(s) for downstream processors
            org.trostheide.svgtoolbox.core.SvgAnalyzer.LayerInfo info = layerInfoMap.get(layerId);
            java.util.Set<String> layerColors = (info != null) ? info.colors : java.util.Collections.singleton(layerId);

            if (!lw.chkExport.isSelected()) {
                for (String c : layerColors) { hiddenLayers.add(c); }
                continue;
            }

            float width = lw.sldStrokeWidth.getValue() / 10f;
            if (width > 0) {
                for (String c : layerColors) { strokeWidthOverrides.put(c, width); }
            }

            String pat = (String) lw.cmbPattern.getSelectedItem();
            if ("none".equals(pat)) {
                for (String c : layerColors) {
                    try { noHatchColors.add(Color.decode(c)); } catch(Exception ignored) {}
                }
            } else if (!"Global".equals(pat)) {
                boolean isCross = "cross".equals(pat);
                for (String c : layerColors) {
                    styleOverrides.put(c, new org.trostheide.svgtoolbox.HatchStyle(45.0, 5.0, isCross));
                }
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
