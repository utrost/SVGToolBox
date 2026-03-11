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

    public ControlPanel(MainWindow parent) {
        this.parent = parent;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(300, 0));

        add(createGlobalSettings());
        add(createGeometrySettings());
        add(Box.createVerticalGlue());

        btnProcess = new JButton("Update Preview");
        btnProcess.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnProcess.addActionListener(e -> updatePreview());
        add(btnProcess);

        btnSave = new JButton("Save As...");
        btnSave.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnSave.addActionListener(e -> saveOutput());
        add(Box.createVerticalStrut(10));
        add(btnSave);

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
            final File tempOut = File.createTempFile("preview_", ".svg");

            String cropVal = (String) cmbCrop.getSelectedItem();
            java.awt.geom.Rectangle2D cropRect = null;

            if ("Current View".equals(cropVal)) {
                cropRect = parent.getPreviewPanel().getViewportBounds();
            } else if (!"None".equals(cropVal)) {
                cropRect = SvgToolboxRunner.parseCrop(cropVal);
            }

            // Build Config from GUI
            final Config config = new Config.Builder()
                    .inputPath(currentInputFile.getAbsolutePath())
                    .outputPath(tempOut.getAbsolutePath())
                    .strokeWidth(sldStrokeWidth.getValue() / 10f)
                    .enableHatching(chkEnableHatching.isSelected())
                    .hatchPattern((String) cmbPattern.getSelectedItem())
                    .rotationDegrees(currentRotation)
                    .cropBounds(cropRect)
                    .optimizePaths(chkOptimize.isSelected())
                    .build();

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
                // Re-run pipeline to the chosen output file
                String cropVal = (String) cmbCrop.getSelectedItem();
                java.awt.geom.Rectangle2D cropRect = null;

                if ("Current View".equals(cropVal)) {
                    cropRect = parent.getPreviewPanel().getViewportBounds();
                } else if (!"None".equals(cropVal)) {
                    cropRect = SvgToolboxRunner.parseCrop(cropVal);
                }

                final Config config = new Config.Builder()
                        .inputPath(currentInputFile.getAbsolutePath())
                        .outputPath(fc.getSelectedFile().getAbsolutePath())
                        .strokeWidth(sldStrokeWidth.getValue() / 10f)
                        .enableHatching(chkEnableHatching.isSelected())
                        .hatchPattern((String) cmbPattern.getSelectedItem())
                        .rotationDegrees(currentRotation)
                        .cropBounds(cropRect)
                        .optimizePaths(chkOptimize.isSelected())
                        .build();

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
