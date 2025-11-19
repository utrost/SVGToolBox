package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.awt.Color;

public class PaletteProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        if (config.palette() == null || config.palette().isEmpty()) return;

        NodeList elements = doc.getElementsByTagName("*");

        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);

            if (el.hasAttribute("stroke")) {
                quantizeAttribute(el, "stroke", config);
            }
            if (el.hasAttribute("fill")) {
                quantizeAttribute(el, "fill", config);
            }
        }
        System.out.println("Quantized colors to palette size: " + config.palette().size());
    }

    private void quantizeAttribute(Element el, String attr, Config config) {
        String val = el.getAttribute(attr);
        if ("none".equalsIgnoreCase(val) || val.isEmpty()) return;

        try {
            Color original = Color.decode(val);
            Color nearest = findNearest(original, config);
            String hex = String.format("#%02x%02x%02x", nearest.getRed(), nearest.getGreen(), nearest.getBlue());
            el.setAttribute(attr, hex);
        } catch (NumberFormatException ignored) {
            // Handle named colors or complex paint servers if necessary
        }
    }

    private Color findNearest(Color target, Config config) {
        Color nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Color p : config.palette()) {
            double dist = Math.pow(target.getRed() - p.getRed(), 2) +
                    Math.pow(target.getGreen() - p.getGreen(), 2) +
                    Math.pow(target.getBlue() - p.getBlue(), 2);
            if (dist < minDistance) {
                minDistance = dist;
                nearest = p;
            }
        }
        return nearest;
    }
}