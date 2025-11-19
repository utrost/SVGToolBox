package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class LayerProcessor implements Processor {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String INKSCAPE_NS = "http://www.inkscape.org/namespaces/inkscape";

    @Override
    public void process(Document doc, Config config) {
        Element root = doc.getDocumentElement();
        root.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:inkscape", INKSCAPE_NS);

        // Map Key (Hex Color) -> Layer Group Element
        Map<String, Element> layers = new HashMap<>();

        // Identify all graphical elements that need moving
        List<Element> movables = new ArrayList<>();
        NodeList all = doc.getElementsByTagName("*");

        for (int i = 0; i < all.getLength(); i++) {
            Element el = (Element) all.item(i);
            // Ignore defs and existing groups for now, target shapes
            if (isShape(el.getTagName()) || "g".equals(el.getTagName())) {
                // Don't move the defs or top level structure
                if(el.getParentNode().getNodeName().equals("svg")) {
                    movables.add(el);
                }
            }
        }

        for (Element el : movables) {
            String color = getStrokeColor(el);
            if (color.equals("none")) continue;

            // Create layer if missing
            if (!layers.containsKey(color)) {
                Element layer = doc.createElementNS(SVG_NS, "g");
                layer.setAttributeNS(INKSCAPE_NS, "inkscape:groupmode", "layer");
                layer.setAttributeNS(INKSCAPE_NS, "inkscape:label", "Color " + color);
                layer.setAttribute("id", "layer_" + color.replace("#", ""));
                root.appendChild(layer);
                layers.put(color, layer);
            }

            // Move element
            layers.get(color).appendChild(el);
        }

        System.out.println("Organized output into " + layers.size() + " layers.");
    }

    private boolean isShape(String tag) {
        return tag.matches("rect|circle|ellipse|line|polyline|polygon|path");
    }

    private String getStrokeColor(Element el) {
        if (el.hasAttribute("stroke")) return el.getAttribute("stroke");
        // If it's a hatch group, the group usually has the stroke
        return "unknown";
    }
}