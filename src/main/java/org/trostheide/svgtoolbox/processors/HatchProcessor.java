package org.trostheide.svgtoolbox.processors;

import org.trostheide.svgtoolbox.Config;
import org.trostheide.svgtoolbox.HatchStyle;
import org.trostheide.svgtoolbox.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.Color;
import java.util.UUID;

public class HatchProcessor implements Processor {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";

    @Override
    public void process(Document doc, Config config) {
        if (!config.enableHatching()) return;

        Element defs = (Element) doc.getElementsByTagName("defs").item(0);
        if (defs == null) {
            defs = doc.createElementNS(SVG_NS, "defs");
            doc.getDocumentElement().insertBefore(defs, doc.getDocumentElement().getFirstChild());
        }

        NodeList elements = doc.getElementsByTagName("*");
        java.util.List<Element> shapesToHatch = new java.util.ArrayList<>();

        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            String fill = el.getAttribute("fill");

            if (fill == null || fill.isEmpty() || "none".equalsIgnoreCase(fill)) continue;
            if (shouldSkipColor(fill, config)) continue;
            if (calculateApproxArea(el) < config.minHatchArea()) continue;

            shapesToHatch.add(el);
        }

        for (Element el : shapesToHatch) {
            applyHatch(doc, defs, el, config);
        }
        System.out.println("Hatched " + shapesToHatch.size() + " shapes.");
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

    private double calculateApproxArea(Element el) {
        Bounds b = getBounds(el);
        return (b.maxX - b.minX) * (b.maxY - b.minY);
    }

    private HatchStyle getStyleFor(String hexColor, Config config) {
        // Normalize hex to lowercase for lookup
        if (hexColor != null) {
            String key = hexColor.toLowerCase();
            if (config.overrides().containsKey(key)) {
                return config.overrides().get(key);
            }
        }
        return config.globalStyle();
    }

    private void applyHatch(Document doc, Element defs, Element target, Config config) {
        String color = target.getAttribute("fill");
        HatchStyle style = getStyleFor(color, config);

        Bounds bounds = getBounds(target);
        double width = bounds.maxX - bounds.minX;
        double height = bounds.maxY - bounds.minY;
        double centerX = bounds.minX + (width / 2.0);
        double centerY = bounds.minY + (height / 2.0);
        double diagonal = Math.sqrt((width * width) + (height * height));
        double radius = diagonal / 2.0;

        // Create ClipPath
        String id = "clip-" + UUID.randomUUID().toString().substring(0, 8);
        Element clipPath = doc.createElementNS(SVG_NS, "clipPath");
        clipPath.setAttribute("id", id);
        Element clone = (Element) target.cloneNode(true);
        clone.removeAttribute("id");
        clone.setAttribute("fill", "none");
        clone.setAttribute("stroke", "none");
        clipPath.appendChild(clone);
        defs.appendChild(clipPath);

        // Create Container Group
        Element mainGroup = doc.createElementNS(SVG_NS, "g");
        mainGroup.setAttribute("clip-path", "url(#" + id + ")");
        mainGroup.setAttribute("stroke", color);
        mainGroup.setAttribute("stroke-width", String.valueOf(config.strokeWidth()));
        if (target.hasAttribute("transform")) {
            mainGroup.setAttribute("transform", target.getAttribute("transform"));
        }

        // Pass 1: Primary Angle
        generateLines(doc, mainGroup, centerX, centerY, radius, style.angle(), style.gap());

        // Pass 2: Cross-Hatch (Perpendicular)
        if (style.crossHatch()) {
            generateLines(doc, mainGroup, centerX, centerY, radius, style.angle() + 90.0, style.gap());
        }

        // Finalize
        target.setAttribute("fill", "none");
        target.getParentNode().insertBefore(mainGroup, target.getNextSibling());
    }

    private void generateLines(Document doc, Element parent, double cx, double cy, double radius, double angle, double gap) {
        // Local Group for rotation
        Element subGroup = doc.createElementNS(SVG_NS, "g");
        String rotate = String.format("rotate(%s, %s, %s)", angle, cx, cy);
        subGroup.setAttribute("transform", rotate);

        double startY = cy - radius - gap;
        double endY = cy + radius + gap;
        double startX = cx - radius;
        double endX = cx + radius;

        for (double y = startY; y < endY; y += gap) {
            Element line = doc.createElementNS(SVG_NS, "line");
            line.setAttribute("x1", String.valueOf(startX));
            line.setAttribute("y1", String.valueOf(y));
            line.setAttribute("x2", String.valueOf(endX));
            line.setAttribute("y2", String.valueOf(y));
            subGroup.appendChild(line);
        }
        parent.appendChild(subGroup);
    }

    private Bounds getBounds(Element el) {
        // [Existing getBounds logic from previous response]
        // Re-insert the exact same logic here (rect, polygon, polyline parsing)
        double minX = -10000, maxX = 10000, minY = -10000, maxY = 10000;
        try {
            if ("rect".equals(el.getTagName())) {
                double x = Double.parseDouble(el.getAttribute("x"));
                double y = Double.parseDouble(el.getAttribute("y"));
                double w = Double.parseDouble(el.getAttribute("width"));
                double h = Double.parseDouble(el.getAttribute("height"));
                return new Bounds(x, x + w, y, y + h);
            }
            else if ("polygon".equals(el.getTagName()) || "polyline".equals(el.getTagName())) {
                String points = el.getAttribute("points");
                String[] pairs = points.trim().split("[\\s,]+");
                double lx = Double.MAX_VALUE, hx = -Double.MAX_VALUE;
                double ly = Double.MAX_VALUE, hy = -Double.MAX_VALUE;
                for (int i = 0; i < pairs.length; i+=2) {
                    if(i+1 >= pairs.length) break;
                    double px = Double.parseDouble(pairs[i]);
                    double py = Double.parseDouble(pairs[i+1]);
                    lx = Math.min(lx, px); hx = Math.max(hx, px);
                    ly = Math.min(ly, py); hy = Math.max(hy, py);
                }
                return new Bounds(lx, hx, ly, hy);
            }
        } catch (Exception e) {}
        return new Bounds(minX, maxX, minY, maxY);
    }
    record Bounds(double minX, double maxX, double minY, double maxY) {}
}