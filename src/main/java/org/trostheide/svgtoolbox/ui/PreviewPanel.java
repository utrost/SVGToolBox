package org.trostheide.svgtoolbox.ui;

import org.apache.batik.swing.JSVGCanvas;
import javax.swing.*;
import java.awt.*;
import java.io.File;

public class PreviewPanel extends JPanel {
    private JSVGCanvas svgCanvas;

    public PreviewPanel() {
        setLayout(new BorderLayout());
        svgCanvas = new JSVGCanvas();
        // Enable interaction if desired (pan/zoom usually requires more setup)
        svgCanvas.setDocumentState(JSVGCanvas.ALWAYS_DYNAMIC);

        add(svgCanvas, BorderLayout.CENTER);

        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    java.util.List<File> files = (java.util.List<File>) support.getTransferable()
                            .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        File file = files.get(0);
                        loadFile(file);
                        // Also notify control panel to update current input
                        // Need a way to traverse up or use an event.
                        // For simplicity, let's assume MainWindow handles coordination or we expose a
                        // listener.
                        // Actually, ControlPanel drives the process. PreviewPanel just displays.
                        // Ideally MainWindow should coordinate.
                        Container parent = getParent();
                        while (parent != null && !(parent instanceof MainWindow)) {
                            parent = parent.getParent();
                        }
                        if (parent instanceof MainWindow) {
                            ((MainWindow) parent).setInputFile(file);
                        }
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        });
    }

    public void loadFile(File file) {
        if (file.exists()) {
            svgCanvas.setURI(file.toURI().toString());
        }
    }

    public JSVGCanvas getCanvas() {
        return svgCanvas;
    }
}
