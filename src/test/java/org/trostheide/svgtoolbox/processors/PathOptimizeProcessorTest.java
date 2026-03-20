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

class PathOptimizeProcessorTest {

    private Config defaultConfig() {
        return new Config.Builder()
                .inputPath("in").outputPath("out")
                .optimizePaths(true)
                .build();
    }

    private Document createDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    @Test
    void testReordersLinesToMinimizeTravel() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // Line A: far from origin (900,900)-(950,950)
        Element lineA = doc.createElement("line");
        lineA.setAttribute("x1", "900"); lineA.setAttribute("y1", "900");
        lineA.setAttribute("x2", "950"); lineA.setAttribute("y2", "950");
        group.appendChild(lineA);

        // Line B: close to origin (10,10)-(20,20)
        Element lineB = doc.createElement("line");
        lineB.setAttribute("x1", "10"); lineB.setAttribute("y1", "10");
        lineB.setAttribute("x2", "20"); lineB.setAttribute("y2", "20");
        group.appendChild(lineB);

        // Line C: medium distance (100,100)-(110,110)
        Element lineC = doc.createElement("line");
        lineC.setAttribute("x1", "100"); lineC.setAttribute("y1", "100");
        lineC.setAttribute("x2", "110"); lineC.setAttribute("y2", "110");
        group.appendChild(lineC);

        new PathOptimizeProcessor().process(doc, defaultConfig());

        // Nearest-neighbor from (0,0) should pick B first, then C, then A
        List<Element> ordered = getChildElements(group);
        assertEquals(3, ordered.size());

        assertEquals("10", ordered.get(0).getAttribute("x1"),
                "Line closest to origin should be first");
        assertEquals("900", ordered.get(2).getAttribute("x1"),
                "Line farthest from chain should be last");
    }

    @Test
    void testSingleElementGroupUnchanged() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        Element line = doc.createElement("line");
        line.setAttribute("x1", "10"); line.setAttribute("y1", "20");
        line.setAttribute("x2", "30"); line.setAttribute("y2", "40");
        group.appendChild(line);

        // Should not throw and element should remain
        new PathOptimizeProcessor().process(doc, defaultConfig());

        List<Element> children = getChildElements(group);
        assertEquals(1, children.size());
        assertEquals("10", children.get(0).getAttribute("x1"));
    }

    @Test
    void testEmptyGroupDoesNotThrow() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // No shapes at all
        assertDoesNotThrow(() ->
                new PathOptimizeProcessor().process(doc, defaultConfig()));
    }

    @Test
    void testHandlesRectsAndCircles() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element group = doc.createElement("g");
        root.appendChild(group);

        // A rect far away
        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "500"); rect.setAttribute("y", "500");
        rect.setAttribute("width", "10"); rect.setAttribute("height", "10");
        group.appendChild(rect);

        // A circle near origin
        Element circle = doc.createElement("circle");
        circle.setAttribute("cx", "5"); circle.setAttribute("cy", "5");
        circle.setAttribute("r", "3");
        group.appendChild(circle);

        new PathOptimizeProcessor().process(doc, defaultConfig());

        List<Element> ordered = getChildElements(group);
        assertEquals(2, ordered.size());
        assertEquals("circle", ordered.get(0).getTagName(),
                "Circle near origin should come first");
        assertEquals("rect", ordered.get(1).getTagName(),
                "Rect far away should come second");
    }

    private List<Element> getChildElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) result.add((Element) n);
        }
        return result;
    }
}
