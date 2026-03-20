package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

class CropProcessorTest {

    private Config configWithCrop(Rectangle2D bounds) {
        return new Config.Builder()
                .inputPath("in").outputPath("out")
                .cropBounds(bounds)
                .build();
    }

    private Document createDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    @Test
    void testRemovesShapesOutsideBounds() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        // Inside the crop area
        Element inside = doc.createElement("rect");
        inside.setAttribute("x", "10"); inside.setAttribute("y", "10");
        inside.setAttribute("width", "50"); inside.setAttribute("height", "50");
        root.appendChild(inside);

        // Completely outside the crop area
        Element outside = doc.createElement("rect");
        outside.setAttribute("x", "500"); outside.setAttribute("y", "500");
        outside.setAttribute("width", "50"); outside.setAttribute("height", "50");
        root.appendChild(outside);

        Config config = configWithCrop(new Rectangle2D.Double(0, 0, 200, 200));

        new CropProcessor().process(doc, config);

        NodeList rects = doc.getElementsByTagName("rect");
        assertEquals(1, rects.getLength(), "Only the inside rect should remain");
        assertEquals("10", ((Element) rects.item(0)).getAttribute("x"));
    }

    @Test
    void testKeepsPartiallyOverlappingShapes() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        // Partially overlaps: extends beyond crop but intersects
        Element partial = doc.createElement("rect");
        partial.setAttribute("x", "150"); partial.setAttribute("y", "150");
        partial.setAttribute("width", "100"); partial.setAttribute("height", "100");
        root.appendChild(partial);

        Config config = configWithCrop(new Rectangle2D.Double(0, 0, 200, 200));

        new CropProcessor().process(doc, config);

        NodeList rects = doc.getElementsByTagName("rect");
        assertEquals(1, rects.getLength(),
                "Partially overlapping shape should be kept");
    }

    @Test
    void testNullCropBoundsSkipsProcessing() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("x", "999"); rect.setAttribute("y", "999");
        rect.setAttribute("width", "10"); rect.setAttribute("height", "10");
        root.appendChild(rect);

        Config config = configWithCrop(null);

        new CropProcessor().process(doc, config);

        NodeList rects = doc.getElementsByTagName("rect");
        assertEquals(1, rects.getLength(),
                "With null crop bounds, nothing should be removed");
    }

    @Test
    void testCropsCirclesOutsideBounds() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element insideCircle = doc.createElement("circle");
        insideCircle.setAttribute("cx", "50"); insideCircle.setAttribute("cy", "50");
        insideCircle.setAttribute("r", "20");
        root.appendChild(insideCircle);

        Element outsideCircle = doc.createElement("circle");
        outsideCircle.setAttribute("cx", "500"); outsideCircle.setAttribute("cy", "500");
        outsideCircle.setAttribute("r", "10");
        root.appendChild(outsideCircle);

        Config config = configWithCrop(new Rectangle2D.Double(0, 0, 200, 200));

        new CropProcessor().process(doc, config);

        NodeList circles = doc.getElementsByTagName("circle");
        assertEquals(1, circles.getLength(), "Only the inside circle should remain");
        assertEquals("50", ((Element) circles.item(0)).getAttribute("cx"));
    }

    @Test
    void testIgnoresNonShapeElements() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        // A group element should not be cropped
        Element group = doc.createElement("g");
        group.setAttribute("id", "mygroup");
        root.appendChild(group);

        // A text element is not in the isShape list
        Element text = doc.createElement("text");
        text.setAttribute("x", "999"); text.setAttribute("y", "999");
        root.appendChild(text);

        Config config = configWithCrop(new Rectangle2D.Double(0, 0, 10, 10));

        new CropProcessor().process(doc, config);

        // Both non-shape elements should survive
        assertNotNull(doc.getElementById("mygroup") != null ? doc.getElementById("mygroup")
                : root.getElementsByTagName("g").item(0),
                "Group elements should not be affected by cropping");
        assertEquals(1, doc.getElementsByTagName("text").getLength(),
                "Text elements should not be affected by cropping");
    }
}
