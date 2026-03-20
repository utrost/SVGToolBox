package org.trostheide.svgtoolbox.processors;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaletteProcessorTest {

    private Config configWithPalette(List<Color> palette) {
        return new Config.Builder()
                .inputPath("in").outputPath("out")
                .palette(palette)
                .build();
    }

    private Document createDoc() throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    @Test
    void testQuantizesToNearestPaletteColor() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        // Dark red (#880000) should map to pure red (#FF0000) not blue (#0000FF)
        Element rect = doc.createElement("rect");
        rect.setAttribute("fill", "#880000");
        rect.setAttribute("stroke", "#000088");
        root.appendChild(rect);

        Config config = configWithPalette(List.of(Color.RED, Color.BLUE));

        new PaletteProcessor().process(doc, config);

        assertEquals("#ff0000", rect.getAttribute("fill"),
                "Dark red should quantize to red");
        assertEquals("#0000ff", rect.getAttribute("stroke"),
                "Dark blue should quantize to blue");
    }

    @Test
    void testPerceptualColorMatching() throws Exception {
        // CIELAB distance should pick perceptually closer colors.
        // Yellow (#FFFF00) is perceptually closer to green (#00FF00) than to blue (#0000FF).
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element line = doc.createElement("line");
        line.setAttribute("stroke", "#FFFF00");
        root.appendChild(line);

        Config config = configWithPalette(List.of(Color.GREEN, Color.BLUE));

        new PaletteProcessor().process(doc, config);

        assertEquals("#00ff00", line.getAttribute("stroke"),
                "Yellow should be perceptually closer to green than blue in CIELAB");
    }

    @Test
    void testNoneAndEmptyPreserved() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element noFill = doc.createElement("rect");
        noFill.setAttribute("fill", "none");
        noFill.setAttribute("stroke", "#FF0000");
        root.appendChild(noFill);

        Element emptyStroke = doc.createElement("rect");
        emptyStroke.setAttribute("fill", "#00FF00");
        emptyStroke.setAttribute("stroke", "");
        root.appendChild(emptyStroke);

        Config config = configWithPalette(List.of(Color.BLACK));

        new PaletteProcessor().process(doc, config);

        assertEquals("none", noFill.getAttribute("fill"),
                "fill='none' should not be quantized");
        assertEquals("", emptyStroke.getAttribute("stroke"),
                "Empty stroke should not be quantized");
    }

    @Test
    void testEmptyPaletteSkipsProcessing() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("fill", "#FF0000");
        root.appendChild(rect);

        Config config = configWithPalette(Collections.emptyList());

        new PaletteProcessor().process(doc, config);

        assertEquals("#FF0000", rect.getAttribute("fill"),
                "With empty palette, colors should remain unchanged");
    }

    @Test
    void testInvalidColorFormatIgnored() throws Exception {
        Document doc = createDoc();
        Element root = doc.createElement("svg");
        doc.appendChild(root);

        Element rect = doc.createElement("rect");
        rect.setAttribute("fill", "url(#gradient1)");
        rect.setAttribute("stroke", "#FF0000");
        root.appendChild(rect);

        Config config = configWithPalette(List.of(Color.BLACK));

        // Should not throw
        new PaletteProcessor().process(doc, config);

        assertEquals("url(#gradient1)", rect.getAttribute("fill"),
                "Non-hex color references should be left untouched");
        assertEquals("#000000", rect.getAttribute("stroke"),
                "Valid hex colors should still be quantized");
    }
}
