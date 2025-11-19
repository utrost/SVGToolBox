package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class StrokeWidthProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        if (config.strokeWidth() <= 0) return;

        String widthStr = String.valueOf(config.strokeWidth());
        NodeList elements = doc.getElementsByTagName("*");

        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            // Apply to common shapes
            if (isShape(el.getTagName())) {
                el.setAttribute("stroke-width", widthStr);
                // Ensure stroke is visible if previously none, though usually handled by Palette
                if (!el.hasAttribute("stroke")) {
                    el.setAttribute("stroke", "#000000");
                }
            }
        }
        System.out.println("Applied stroke-width: " + widthStr);
    }

    private boolean isShape(String tag) {
        return tag.matches("rect|circle|ellipse|line|polyline|polygon|path");
    }
}