package org.trostheide.svgtoolbox.core;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class SvgAnalyzer {

    public static Set<String> extractLayerColors(File svgFile) throws Exception {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        Document doc = factory.createDocument(svgFile.toURI().toString());

        Set<String> colors = new HashSet<>();
        collectColors(doc.getDocumentElement(), colors);
        return colors;
    }

    private static void collectColors(Element parent, Set<String> colors) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element el = (Element) node;
                
                String color = getStrokeColor(el);
                if (color != null && !color.equals("none") && !color.equals("unknown")) {
                    colors.add(color.toLowerCase());
                }
                
                collectColors(el, colors);
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
