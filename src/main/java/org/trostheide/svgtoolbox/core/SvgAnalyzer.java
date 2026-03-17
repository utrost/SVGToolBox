package org.trostheide.svgtoolbox.core;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SvgAnalyzer {

    public static Map<String, String> extractLayerNames(File svgFile) throws Exception {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        Document doc = factory.createDocument(svgFile.toURI().toString());

        Map<String, String> layerNames = new HashMap<>();
        collectLayerNames(doc.getDocumentElement(), layerNames, "Unknown Layer");
        return layerNames;
    }

    private static void collectLayerNames(Element parent, Map<String, String> layerNames, String currentLayerName) {
        // If this element is a layer/group, update the name we will associate with colors found inside
        String newLayerName = currentLayerName;
        if ("g".equals(parent.getTagName()) || "layer".equals(parent.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "groupmode"))) {
            if (parent.hasAttributeNS("http://www.inkscape.org/namespaces/inkscape", "label")) {
                newLayerName = parent.getAttributeNS("http://www.inkscape.org/namespaces/inkscape", "label");
            } else if (parent.hasAttribute("id") && !parent.getAttribute("id").isEmpty()) {
                newLayerName = parent.getAttribute("id");
            }
        }

        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element el = (Element) node;
                
                String color = getStrokeColor(el);
                if (color != null && !color.equals("none") && !color.equals("unknown")) {
                    String hexColor = color.toLowerCase();
                    // Keep the first layer name we associate with this color, or update if we was "Unknown Layer" maybe?
                    // Actually, if multiple things use the same color in different layers, 
                    // this will overwrite or keep first. Let's just putIfAbsent.
                    layerNames.putIfAbsent(hexColor, newLayerName);
                }
                
                collectLayerNames(el, layerNames, newLayerName);
            }
        }
    }

    private static String getStrokeColor(Element el) {
        if (el.hasAttribute("stroke") && !"none".equalsIgnoreCase(el.getAttribute("stroke"))) {
            return el.getAttribute("stroke");
        }
        if (isShape(el.getTagName()) && el.hasAttribute("fill") && !"none".equalsIgnoreCase(el.getAttribute("fill"))) {
            return el.getAttribute("fill");
        }
        return "unknown";
    }

    private static boolean isShape(String tag) {
        return tag.matches("rect|circle|ellipse|line|polyline|polygon|path");
    }
}
