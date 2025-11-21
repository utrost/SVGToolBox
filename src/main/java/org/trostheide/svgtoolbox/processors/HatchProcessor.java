package org.trostheide.svgtoolbox.processors;

import org.apache.batik.parser.AWTPathProducer;
import org.apache.batik.parser.AWTTransformProducer;
import org.apache.batik.parser.PathParser;
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
        if (!config.enableHatching()) return;

        NodeList elements = doc.getElementsByTagName("*");
        List<Element> shapesToHatch = new ArrayList<>();

        // 1. Identify candidates
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            String fill = el.getAttribute("fill");

            if (fill == null || fill.isEmpty() || "none".equalsIgnoreCase(fill)) continue;
            if (shouldSkipColor(fill, config)) continue;

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
        if (config.noHatchColors().isEmpty()) return false;
        try {
            Color c = Color.decode(hex);
            for (Color skip : config.noHatchColors()) {
                if (c.equals(skip)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean hatchShape(Document doc, Element target, Config config) {
        // A. Get Local Geometry
        Shape localShape = parseGeometry(target);
        if (localShape == null) return false;

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

        // E. Generate Lines (Scanline Algorithm)
        Element group = doc.createElementNS(SVG_NS, "g");
        group.setAttribute("stroke", color);

        double finalStrokeWidth = config.strokeWidth() > 0 ? config.strokeWidth() : 1.0;
        group.setAttribute("stroke-width", String.format(Locale.US, "%.2f", finalStrokeWidth));

        // Pass 1 & 2 (using world shape)
        List<Line2D> lines = scanlineHatch(worldShape, style.angle(), style.gap());
        if (style.crossHatch()) {
            lines.addAll(scanlineHatch(worldShape, style.angle() + 90.0, style.gap()));
        }

        // F. Serialize lines using US Locale to ensure dots, not commas
        for (Line2D l : lines) {
            Element line = doc.createElementNS(SVG_NS, "line");
            line.setAttribute("x1", String.format(Locale.US, "%.4f", l.getX1()));
            line.setAttribute("y1", String.format(Locale.US, "%.4f", l.getY1()));
            line.setAttribute("x2", String.format(Locale.US, "%.4f", l.getX2()));
            line.setAttribute("y2", String.format(Locale.US, "%.4f", l.getY2()));
            group.appendChild(line);
        }

        // G. Replace Original
        // Append to root to avoid re-applying parent transforms
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
                if (t != null) at.concatenate(t);
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
        } catch (Exception e) { return null; }
    }

    // --- Standard Hatch Logic ---

    private List<Line2D> scanlineHatch(Shape shape, double angleDeg, double gap) {
        List<Line2D> result = new ArrayList<>();
        AffineTransform toAligned = AffineTransform.getRotateInstance(Math.toRadians(-angleDeg));
        Shape alignedShape = toAligned.createTransformedShape(shape);
        Rectangle2D bounds = alignedShape.getBounds2D();
        double startY = bounds.getMinY();
        double endY = bounds.getMaxY();

        PathIterator pi = alignedShape.getPathIterator(null, 0.5);
        List<Line2D> edges = flattenPath(pi);

        for (double y = startY + gap; y < endY; y += gap) {
            List<Double> intersections = new ArrayList<>();
            for (Line2D edge : edges) {
                if (lineIntersectsY(edge, y)) {
                    double x = getXAtY(edge, y);
                    intersections.add(x);
                }
            }
            Collections.sort(intersections);
            for (int i = 0; i < intersections.size() - 1; i += 2) {
                double x1 = intersections.get(i);
                double x2 = intersections.get(i + 1);
                Point2D p1 = new Point2D.Double(x1, y);
                Point2D p2 = new Point2D.Double(x2, y);
                try {
                    AffineTransform toWorld = toAligned.createInverse();
                    Point2D w1 = toWorld.transform(p1, null);
                    Point2D w2 = toWorld.transform(p2, null);
                    result.add(new Line2D.Double(w1, w2));
                } catch (NoninvertibleTransformException e) {}
            }
        }
        return result;
    }

    private boolean lineIntersectsY(Line2D l, double y) {
        return (l.getY1() <= y && l.getY2() > y) || (l.getY2() <= y && l.getY1() > y);
    }

    private double getXAtY(Line2D l, double y) {
        double dy = l.getY2() - l.getY1();
        if (Math.abs(dy) < 0.00001) return l.getX1();
        return l.getX1() + (y - l.getY1()) * (l.getX2() - l.getX1()) / dy;
    }

    private List<Line2D> flattenPath(PathIterator pi) {
        List<Line2D> edges = new ArrayList<>();
        double[] coords = new double[6];
        double startX = 0, startY = 0;
        double currX = 0, currY = 0;
        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    startX = currX = coords[0];
                    startY = currY = coords[1];
                    break;
                case PathIterator.SEG_LINETO:
                    edges.add(new Line2D.Double(currX, currY, coords[0], coords[1]));
                    currX = coords[0];
                    currY = coords[1];
                    break;
                case PathIterator.SEG_CLOSE:
                    edges.add(new Line2D.Double(currX, currY, startX, startY));
                    currX = startX;
                    currY = startY;
                    break;
            }
            pi.next();
        }
        return edges;
    }

    private HatchStyle getStyleFor(String hex, Config config) {
        if (hex != null && config.overrides().containsKey(hex.toLowerCase())) {
            return config.overrides().get(hex.toLowerCase());
        }
        return config.globalStyle();
    }

    private Shape parseGeometry(Element el) {
        String tag = el.getTagName();
        try {
            if ("rect".equals(tag)) {
                double x = Double.parseDouble(el.getAttribute("x"));
                double y = Double.parseDouble(el.getAttribute("y"));
                double w = Double.parseDouble(el.getAttribute("width"));
                double h = Double.parseDouble(el.getAttribute("height"));
                return new Rectangle2D.Double(x, y, w, h);
            } else if ("circle".equals(tag)) {
                double cx = Double.parseDouble(el.getAttribute("cx"));
                double cy = Double.parseDouble(el.getAttribute("cy"));
                double r = Double.parseDouble(el.getAttribute("r"));
                return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
            } else if ("ellipse".equals(tag)) {
                double cx = Double.parseDouble(el.getAttribute("cx"));
                double cy = Double.parseDouble(el.getAttribute("cy"));
                double rx = Double.parseDouble(el.getAttribute("rx"));
                double ry = Double.parseDouble(el.getAttribute("ry"));
                return new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2);
            } else if ("polygon".equals(tag) || "polyline".equals(tag)) {
                String points = el.getAttribute("points");
                String[] pairs = points.trim().split("[\\s,]+");
                Path2D p = new Path2D.Double();
                if (pairs.length >= 2) {
                    p.moveTo(Double.parseDouble(pairs[0]), Double.parseDouble(pairs[1]));
                    for (int i = 2; i < pairs.length; i += 2) {
                        p.lineTo(Double.parseDouble(pairs[i]), Double.parseDouble(pairs[i + 1]));
                    }
                    if ("polygon".equals(tag)) p.closePath();
                }
                return p;
            } else if ("path".equals(tag)) {
                String d = el.getAttribute("d");
                PathParser parser = new PathParser();
                AWTPathProducer producer = new AWTPathProducer();
                parser.setPathHandler(producer);
                parser.parse(d);
                return producer.getShape();
            }
        } catch (Exception e) {}
        return null;
    }
}