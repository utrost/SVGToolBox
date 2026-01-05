package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class PathOptimizeProcessor implements Processor {

    @Override
    public void process(Document doc, Config config) {
        System.out.println("Optimizing path order...");
        // Traverse looking for groups to optimize
        // For simplicity, we can just process the root or layer groups if we assume
        // LayerProcessor ran first?
        // But SvgToolboxRunner adds this BEFORE LayerProcessor if I recall correctly?
        // No, I added it AFTER LayerProcessor in my plan implementation.
        // Wait, SvgToolboxRunner.java:
        // pipeline.add(new LayerProcessor());
        // pipeline.add(new CropProcessor());
        // if (config.optimizePaths()) pipeline.add(new PathOptimizeProcessor());

        // So optimizing after layers. This is good. We should optimize each Layer
        // group.

        NodeList groups = doc.getElementsByTagName("g");
        for (int i = 0; i < groups.getLength(); i++) {
            optimizeGroup((Element) groups.item(i));
        }

        // Also optimize root if it has direct children?
        // LayerProcessor puts everything in groups, so this should suffice.
    }

    private void optimizeGroup(Element group) {
        List<Element> shapes = new ArrayList<>();
        NodeList kids = group.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n instanceof Element) {
                String tag = ((Element) n).getTagName();
                if (tag.equals("path") || tag.equals("line") || tag.equals("polyline") ||
                        tag.equals("polygon") || tag.equals("rect") || tag.equals("circle") || tag.equals("ellipse")) {
                    shapes.add((Element) n);
                }
            }
        }

        if (shapes.size() < 2)
            return;

        // Nearest Neighbor Greedy Sort
        List<Element> sorted = new ArrayList<>();
        List<Element> remaining = new ArrayList<>(shapes);

        Point2D.Double currentPos = new Point2D.Double(0, 0);

        // Heuristic: Start with the shape closest to 0,0? Or just the first one?
        // Let's find start closest to 0,0
        Element first = findClosest(currentPos, remaining);
        sorted.add(first);
        remaining.remove(first);
        currentPos = getEndPoint(first);

        while (!remaining.isEmpty()) {
            Element next = findClosest(currentPos, remaining);
            sorted.add(next);
            remaining.remove(next);
            currentPos = getEndPoint(next);
        }

        // Reorder in DOM
        for (Element s : sorted) {
            group.appendChild(s); // Moves it to the end
        }
    }

    private Element findClosest(Point2D.Double p, List<Element> candidates) {
        Element best = null;
        double minDistSq = Double.MAX_VALUE;

        for (Element e : candidates) {
            Point2D.Double start = getStartPoint(e);
            double d = p.distanceSq(start);
            if (d < minDistSq) {
                minDistSq = d;
                best = e;
            }
        }
        return best;
    }

    private Point2D.Double getStartPoint(Element e) {
        return getPoint(e, true);
    }

    private Point2D.Double getEndPoint(Element e) {
        return getPoint(e, false);
    }

    private Point2D.Double getPoint(Element e, boolean start) {
        String tag = e.getTagName();
        try {
            if (tag.equals("line")) {
                double x = Double.parseDouble(e.getAttribute(start ? "x1" : "x2"));
                double y = Double.parseDouble(e.getAttribute(start ? "y1" : "y2"));
                return new Point2D.Double(x, y);
            } else if (tag.equals("path")) {
                // Parse 'd'. Simple heuristic: finding first M/L or last coordinates.
                String d = e.getAttribute("d");
                if (d.isEmpty())
                    return new Point2D.Double(0, 0);

                // Very naive parsing for speed. Ideally use Batik's parser.
                // Or better: HatchProcessor produces lines, so mostly lines?
                // But SimplifyProcessor produces paths.

                // Let's try to extract numbers.
                // M x y ...
                // If it's a complicated path, end point is tricky without full parse.
                // Assuming standard "M x y ... L x y"

                String[] tokens = d.replaceAll("[A-Za-z]", " ").trim().split("\\s+");
                if (tokens.length >= 2) {
                    if (start) {
                        return new Point2D.Double(Double.parseDouble(tokens[0]), Double.parseDouble(tokens[1]));
                    } else {
                        // Last two numbers
                        return new Point2D.Double(Double.parseDouble(tokens[tokens.length - 2]),
                                Double.parseDouble(tokens[tokens.length - 1]));
                    }
                }
            } else if (tag.equals("rect")) {
                double x = Double.parseDouble(e.getAttribute("x"));
                double y = Double.parseDouble(e.getAttribute("y"));
                // Start is Top-Left. End is... Top-Left? Or should we trace perim?
                // For plotting "rect" is usually converted to path.
                // But if it remains rect, pen usually ends where it started?
                return new Point2D.Double(x, y);
            }
            // Add other shapes defaults
        } catch (Exception ex) {
            // ignore
        }
        return new Point2D.Double(0, 0);
    }
}
