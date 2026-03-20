package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LayerProcessorTest {

    private static final String INKSCAPE_NS = "http://www.inkscape.org/namespaces/inkscape";

    private Config defaultConfig() {
        return new Config.Builder()
                .inputPath("in").outputPath("out")
                .build();
    }

    private Document createDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    @Test
    void testGroupsShapesByStrokeColor() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element line1 = doc.createElement("line");
        line1.setAttribute("stroke", "#ff0000");
        line1.setAttribute("x1", "0"); line1.setAttribute("y1", "0");
        line1.setAttribute("x2", "100"); line1.setAttribute("y2", "100");
        root.appendChild(line1);

        Element line2 = doc.createElement("line");
        line2.setAttribute("stroke", "#0000ff");
        line2.setAttribute("x1", "50"); line2.setAttribute("y1", "50");
        line2.setAttribute("x2", "150"); line2.setAttribute("y2", "150");
        root.appendChild(line2);

        Element line3 = doc.createElement("line");
        line3.setAttribute("stroke", "#ff0000");
        line3.setAttribute("x1", "10"); line3.setAttribute("y1", "10");
        line3.setAttribute("x2", "90"); line3.setAttribute("y2", "90");
        root.appendChild(line3);

        new LayerProcessor().process(doc, defaultConfig());

        // Should create 2 Inkscape layer groups
        List<Element> layers = getInkscapeLayers(root);
        assertEquals(2, layers.size(), "Should create two layer groups (red, blue)");

        // Find red layer
        Element redLayer = layers.stream()
                .filter(l -> l.getAttribute("id").contains("ff0000"))
                .findFirst().orElse(null);
        assertNotNull(redLayer, "Should have a red layer");

        // Red layer should contain 2 lines
        int redLines = countChildElements(redLayer, "line");
        assertEquals(2, redLines, "Red layer should contain 2 lines");

        // Blue layer should contain 1 line
        Element blueLayer = layers.stream()
                .filter(l -> l.getAttribute("id").contains("0000ff"))
                .findFirst().orElse(null);
        assertNotNull(blueLayer, "Should have a blue layer");
        assertEquals(1, countChildElements(blueLayer, "line"),
                "Blue layer should contain 1 line");
    }

    @Test
    void testSetsInkscapeAttributes() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element line = doc.createElement("line");
        line.setAttribute("stroke", "#00ff00");
        line.setAttribute("x1", "0"); line.setAttribute("y1", "0");
        line.setAttribute("x2", "50"); line.setAttribute("y2", "50");
        root.appendChild(line);

        new LayerProcessor().process(doc, defaultConfig());

        List<Element> layers = getInkscapeLayers(root);
        assertEquals(1, layers.size());

        Element layer = layers.get(0);
        assertEquals("layer", layer.getAttributeNS(INKSCAPE_NS, "groupmode"));
        assertTrue(layer.getAttributeNS(INKSCAPE_NS, "label").contains("#00ff00"),
                "Label should contain the color hex");
        assertEquals("layer_00ff00", layer.getAttribute("id"));
    }

    @Test
    void testInheritsStrokeFromParentGroup() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        // Parent group has stroke color, children don't
        Element group = doc.createElement("g");
        group.setAttribute("stroke", "#ff00ff");
        root.appendChild(group);

        Element line = doc.createElement("line");
        line.setAttribute("x1", "0"); line.setAttribute("y1", "0");
        line.setAttribute("x2", "100"); line.setAttribute("y2", "100");
        group.appendChild(line);

        new LayerProcessor().process(doc, defaultConfig());

        List<Element> layers = getInkscapeLayers(root);
        assertEquals(1, layers.size());

        // The line should now have stroke baked on
        assertEquals("#ff00ff", line.getAttribute("stroke"),
                "Stroke color should be baked onto the shape from parent group");
    }

    @Test
    void testRemovesEmptyGroups() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element emptyGroup = doc.createElement("g");
        emptyGroup.setAttribute("id", "should-be-removed");
        root.appendChild(emptyGroup);

        Element line = doc.createElement("line");
        line.setAttribute("stroke", "#000000");
        line.setAttribute("x1", "0"); line.setAttribute("y1", "0");
        line.setAttribute("x2", "10"); line.setAttribute("y2", "10");
        emptyGroup.appendChild(line);

        new LayerProcessor().process(doc, defaultConfig());

        // The empty group should be removed after the line was moved to a layer
        NodeList groups = root.getElementsByTagName("g");
        for (int i = 0; i < groups.getLength(); i++) {
            Element g = (Element) groups.item(i);
            assertNotEquals("should-be-removed", g.getAttribute("id"),
                    "Empty original group should be cleaned up");
        }
    }

    @Test
    void testUpdatesViewBox() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element line = doc.createElement("line");
        line.setAttribute("stroke", "#000000");
        line.setAttribute("x1", "100"); line.setAttribute("y1", "200");
        line.setAttribute("x2", "300"); line.setAttribute("y2", "400");
        root.appendChild(line);

        new LayerProcessor().process(doc, defaultConfig());

        assertTrue(root.hasAttribute("viewBox"), "Should set viewBox");
        assertTrue(root.hasAttribute("width"), "Should set width");
        assertTrue(root.hasAttribute("height"), "Should set height");

        String viewBox = root.getAttribute("viewBox");
        assertFalse(viewBox.isEmpty(), "viewBox should not be empty");
    }

    @Test
    void testSkipsShapesWithNoColor() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        // Shape with fill="none" and no stroke → getStrokeColor returns "unknown"
        Element rect = doc.createElement("rect");
        rect.setAttribute("fill", "none");
        rect.setAttribute("x", "0"); rect.setAttribute("y", "0");
        rect.setAttribute("width", "50"); rect.setAttribute("height", "50");
        root.appendChild(rect);

        new LayerProcessor().process(doc, defaultConfig());

        List<Element> layers = getInkscapeLayers(root);
        assertEquals(0, layers.size(),
                "Shapes with no identifiable color should not create layers");
    }

    private List<Element> getInkscapeLayers(Element root) {
        List<Element> layers = new ArrayList<>();
        NodeList groups = root.getElementsByTagName("g");
        for (int i = 0; i < groups.getLength(); i++) {
            Element g = (Element) groups.item(i);
            if ("layer".equals(g.getAttributeNS(INKSCAPE_NS, "groupmode"))) {
                layers.add(g);
            }
        }
        return layers;
    }

    private int countChildElements(Element parent, String tagName) {
        int count = 0;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element && tagName.equals(((Element) n).getTagName())) {
                count++;
            }
        }
        return count;
    }
}
