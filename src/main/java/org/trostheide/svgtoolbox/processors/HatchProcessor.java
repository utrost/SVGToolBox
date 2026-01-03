package org.trostheide.svgtoolbox.processors;

import org.apache.batik.parser.AWTTransformProducer;
import org.apache.batik.parser.TransformListParser;
import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HatchProcessor implements Processor {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    @Override
    public void process(Document doc, Config config) {
        if (!config.enableHatching())
            return;

        NodeList elements = doc.getElementsByTagName("*");
        List<Element> shapesToHatch = new ArrayList<>();

        // 1. Identify candidates
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            String fill = el.getAttribute("fill");

            if (fill == null || fill.isEmpty() || "none".equalsIgnoreCase(fill))
                continue;
            if (shouldSkipColor(fill, config))
                continue;

            shapesToHatch.add(el);
        }

        // 2. Process
        int count = 0;
        for (Element el : shapesToHatch) {
            try {
                if (hatchShape(doc, el, config)) {
                    count++;
                }
            } catch (Exception e) {
                System.err.println("Skipped shape due to error: " + e.getMessage());
            }
        }
        System.out.println("Baked geometry for " + count + " shapes.");
    }

    private boolean shouldSkipColor(String hex, Config config) {
        if (config.noHatchColors().isEmpty())
            return false;
        try {
            Color c = Color.decode(hex);
            for (Color skip : config.noHatchColors()) {
                if (c.equals(skip))
                    return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean hatchShape(Document doc, Element target, Config config) {
        // A. Get Local Geometry
        Shape localShape = org.trostheide.svgtoolbox.core.ShapeParser.parse(target);
        if (localShape == null)
            return false;

        // B. Calculate World Transform (Cumulative up to root)
        AffineTransform globalTransform = getGlobalTransform(target);

        // C. Convert Shape to World Space
        Shape worldShape = globalTransform.createTransformedShape(localShape);

        // D. Check Area Filter (in World Space pixels)
        Rectangle2D bounds = worldShape.getBounds2D();
        if ((bounds.getWidth() * bounds.getHeight()) < config.minHatchArea()) {
            return false;
        }

        String color = target.getAttribute("fill");
        HatchStyle style = getStyleFor(color, config);

        // E. Generate Lines (Delegated to Strategy)
        org.trostheide.svgtoolbox.patterns.HatchPattern pattern;

        String patternName = config.hatchPattern().toLowerCase();
        switch (patternName) {
            case "cross":
                pattern = new org.trostheide.svgtoolbox.patterns.CrossHatchPattern();
                break;
            case "zigzag":
                pattern = new org.trostheide.svgtoolbox.patterns.ZigZagHatchPattern();
                break;
            case "wave":
                pattern = new org.trostheide.svgtoolbox.patterns.WaveHatchPattern();
                break;
            case "dot":
                pattern = new org.trostheide.svgtoolbox.patterns.DotHatchPattern();
                break;
            case "linear":
            default:
                if (style.crossHatch()) {
                    pattern = new org.trostheide.svgtoolbox.patterns.CrossHatchPattern();
                } else {
                    pattern = new org.trostheide.svgtoolbox.patterns.LinearHatchPattern();
                }
                break;
        }

        List<Shape> hatchShapes = pattern.generate(worldShape, config, style);

        // F. Serialize lines
        Element group = doc.createElementNS(SVG_NS, "g");
        group.setAttribute("stroke", color);

        double finalStrokeWidth = config.strokeWidth() > 0 ? config.strokeWidth() : 1.0;
        group.setAttribute("stroke-width", String.format(Locale.US, "%.2f", finalStrokeWidth));

        for (Shape s : hatchShapes) {
            if (s instanceof Line2D) {
                Line2D l = (Line2D) s;
                Element line = doc.createElementNS(SVG_NS, "line");
                line.setAttribute("x1", String.format(Locale.US, "%.4f", l.getX1()));
                line.setAttribute("y1", String.format(Locale.US, "%.4f", l.getY1()));
                line.setAttribute("x2", String.format(Locale.US, "%.4f", l.getX2()));
                line.setAttribute("y2", String.format(Locale.US, "%.4f", l.getY2()));
                group.appendChild(line);
            } else if (s instanceof org.apache.batik.ext.awt.geom.ExtendedGeneralPath
                    || s instanceof java.awt.geom.Path2D) {
                // Serialize Paths (ZigZag, Wave)
                java.awt.geom.PathIterator pi = s.getPathIterator(null);
                StringBuilder d = new StringBuilder();
                double[] c = new double[6];
                while (!pi.isDone()) {
                    int type = pi.currentSegment(c);
                    switch (type) {
                        case java.awt.geom.PathIterator.SEG_MOVETO:
                            d.append(String.format(Locale.US, "M%.4f,%.4f ", c[0], c[1]));
                            break;
                        case java.awt.geom.PathIterator.SEG_LINETO:
                            d.append(String.format(Locale.US, "L%.4f,%.4f ", c[0], c[1]));
                            break;
                        case java.awt.geom.PathIterator.SEG_CLOSE:
                            d.append("Z ");
                            break;
                        // Quad/Cubic not generated by our patterns, but good to handle?
                    }
                    pi.next();
                }
                Element path = doc.createElementNS(SVG_NS, "path");
                path.setAttribute("d", d.toString().trim());
                path.setAttribute("fill", "none"); // Ensure hatch lines are strokes only
                group.appendChild(path);
            } else if (s instanceof java.awt.geom.Ellipse2D) {
                // Serialize Dots
                java.awt.geom.Ellipse2D e = (java.awt.geom.Ellipse2D) s;
                Element circle = doc.createElementNS(SVG_NS, "circle");
                circle.setAttribute("cx", String.format(Locale.US, "%.4f", e.getCenterX()));
                circle.setAttribute("cy", String.format(Locale.US, "%.4f", e.getCenterY()));
                circle.setAttribute("r", String.format(Locale.US, "%.4f", e.getWidth() / 2.0));
                // Dots should probably be filled? Or stroked?
                // If it's a stipple, it's usually small filled dots.
                // But the group has 'stroke' color.
                // Let's set fill to current color, stroke to none for DOTS.
                circle.setAttribute("fill", color);
                circle.setAttribute("stroke", "none");
                group.appendChild(circle);
            }
        }

        // G. Replace Original
        doc.getDocumentElement().appendChild(group);
        target.getParentNode().removeChild(target);

        return true;
    }

    // --- Transform Logic ---

    private AffineTransform getGlobalTransform(Element el) {
        AffineTransform at = new AffineTransform();
        List<Element> ancestors = new ArrayList<>();

        Node current = el;
        while (current instanceof Element && !"svg".equals(current.getNodeName())) {
            ancestors.add((Element) current);
            current = current.getParentNode();
        }
        Collections.reverse(ancestors); // Root -> Leaf

        for (Element e : ancestors) {
            if (e.hasAttribute("transform")) {
                AffineTransform t = parseTransform(e.getAttribute("transform"));
                if (t != null)
                    at.concatenate(t);
            }
        }
        return at;
    }

    private AffineTransform parseTransform(String val) {
        try {
            AWTTransformProducer producer = new AWTTransformProducer();
            TransformListParser parser = new TransformListParser();
            parser.setTransformListHandler(producer);
            parser.parse(val);
            return producer.getAffineTransform();
        } catch (Exception e) {
            return null;
        }
    }

    private HatchStyle getStyleFor(String hex, Config config) {
        if (hex != null && config.overrides().containsKey(hex.toLowerCase())) {
            return config.overrides().get(hex.toLowerCase());
        }
        return config.globalStyle();
    }

}