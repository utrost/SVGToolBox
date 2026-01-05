package org.trostheide.svgtoolbox;

import org.apache.commons.cli.*;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.util.XMLResourceDescriptor;
import org.trostheide.svgtoolbox.processors.*;
import org.w3c.dom.Document;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.Color;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class SvgToolboxRunner {

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(Option.builder("i").longOpt("input").hasArg().required().desc("Input SVG path").build());
        options.addOption(Option.builder("o").longOpt("output").hasArg().required().desc("Output SVG path").build());
        options.addOption(Option.builder("w").longOpt("stroke-width").hasArg().type(Number.class)
                .desc("Stroke width (px)").build());
        options.addOption(
                Option.builder("p").longOpt("palette").hasArg().desc("Hex colors (e.g. #000000,#FF0000)").build());

        // Hatching Flags
        options.addOption(Option.builder("h").longOpt("hatch").desc("Enable hatching").build());
        options.addOption(
                Option.builder().longOpt("hatch-angle").hasArg().type(Number.class).desc("Global angle").build());
        options.addOption(Option.builder().longOpt("hatch-gap").hasArg().type(Number.class).desc("Global gap").build());
        options.addOption(
                Option.builder("S").longOpt("style").hasArg().desc("Overrides: HEX:ANGLE:GAP:CROSS;...").build());

        options.addOption(Option.builder().longOpt("no-hatch").hasArg().desc("Colors to skip").build());
        options.addOption(
                Option.builder().longOpt("min-area").hasArg().type(Number.class).desc("Min area (px^2)").build());

        // Optimization Flags
        options.addOption(Option.builder().longOpt("simplify").hasArg().type(Number.class)
                .desc("Simplify tolerance (e.g., 0.5)").build());
        options.addOption(Option.builder().longOpt("pattern").hasArg()
                .desc("Hatch pattern (linear, cross, zigzag, wave, dot)").build());
        options.addOption(Option.builder().longOpt("rotate").hasArg().type(Number.class)
                .desc("Rotate degrees (90, 180...)").build());
        options.addOption(Option.builder().longOpt("stats").desc("Print statistics").build());
        options.addOption(Option.builder().longOpt("crop").hasArg().desc("Crop (WxH, A4, Letter)").build());
        options.addOption(Option.builder().longOpt("optimize").desc("Optimize path order for plotting").build());

        CommandLineParser parser = new DefaultParser();

        try {
            CommandLine cmd = parser.parse(options, args);
            Config config = buildConfig(cmd);
            processPipeline(config);
        } catch (ParseException e) {
            System.err.println("CLI Error: " + e.getMessage());
            new HelpFormatter().printHelp("svgtoolbox", options);
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static Config buildConfig(CommandLine cmd) {
        List<Color> palette = parseColors(cmd.getOptionValue("p"));
        List<Color> noHatch = parseColors(cmd.getOptionValue("no-hatch"));

        // 1. Build Global Defaults
        double defAngle = cmd.hasOption("hatch-angle") ? Double.parseDouble(cmd.getOptionValue("hatch-angle")) : 45.0;
        double defGap = cmd.hasOption("hatch-gap") ? Double.parseDouble(cmd.getOptionValue("hatch-gap")) : 5.0;
        HatchStyle globalStyle = new HatchStyle(defAngle, defGap, false);

        // 2. Parse Overrides (Format: #HEX:ANGLE:GAP:CROSS;...)
        Map<String, HatchStyle> overrides = new HashMap<>();
        if (cmd.hasOption("style")) {
            String[] entries = cmd.getOptionValue("style").split(";");
            for (String entry : entries) {
                String[] parts = entry.split(":");
                if (parts.length >= 3) {
                    String hex = parts[0].trim().toLowerCase();
                    double angle = Double.parseDouble(parts[1]);
                    double gap = Double.parseDouble(parts[2]);
                    boolean cross = parts.length > 3 && Boolean.parseBoolean(parts[3]);
                    overrides.put(hex, new HatchStyle(angle, gap, cross));
                }
            }
        }

        return new Config(
                cmd.getOptionValue("i"),
                cmd.getOptionValue("o"),
                (float) Double.parseDouble(cmd.getOptionValue("stroke-width", "0")),
                palette,
                cmd.hasOption("h"),
                globalStyle,
                overrides,
                noHatch,
                Double.parseDouble(cmd.getOptionValue("min-area", "100.0")),
                Double.parseDouble(cmd.getOptionValue("simplify", "0.0")),
                cmd.getOptionValue("pattern", "linear"),
                Double.parseDouble(cmd.getOptionValue("rotate", "0.0")),
                cmd.hasOption("stats"),
                parseCrop(cmd.getOptionValue("crop")),
                cmd.hasOption("optimize"));
    }

    public static java.awt.geom.Rectangle2D parseCrop(String arg) {
        if (arg == null || arg.isEmpty())
            return null;
        // Format: WxH or "A4"
        double w = 0, h = 0;
        switch (arg.toUpperCase()) {
            case "A4":
                w = 595.28;
                h = 841.89;
                break; // 72 DPI? Or 96? A4 is 210x297mm.
                       // 210mm * 3.7795 px/mm (96dpi) = 793.7.
                       // Standard SVG usually 96dpi.
                       // Let's use 96 DPI: 793.7 x 1122.5
                       // Let's stick to generic units or pixels.
                       // Inkscape uses 96 dpi.
            case "LETTER":
                w = 816.0;
                h = 1056.0;
                break;
            default:
                try {
                    String[] parts = arg.split("x");
                    w = Double.parseDouble(parts[0]);
                    h = Double.parseDouble(parts[1]);
                } catch (Exception e) {
                    return null;
                }
        }
        return new java.awt.geom.Rectangle2D.Double(0, 0, w, h);
    }

    private static List<Color> parseColors(String arg) {
        if (arg == null || arg.isEmpty())
            return new ArrayList<>();
        return Arrays.stream(arg.split(","))
                .map(String::trim)
                .map(Color::decode)
                .collect(Collectors.toList());
    }

    public static void processPipeline(Config config) throws IOException {
        String parser = XMLResourceDescriptor.getXMLParserClassName();
        SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
        Document doc = factory.createDocument(new File(config.inputPath()).toURI().toString());

        List<Processor> pipeline = new ArrayList<>();
        pipeline.add(new StyleNormalizerProcessor());
        pipeline.add(new RotateProcessor());
        pipeline.add(new StrokeWidthProcessor());
        pipeline.add(new PaletteProcessor());

        // 1. Simplify paths to remove noise
        pipeline.add(new SimplifyProcessor());

        // 2. Generate hatched lines (inside groups with transforms)
        pipeline.add(new HatchProcessor());

        // 4. Organize into layers
        pipeline.add(new LayerProcessor());

        // 5. Crop
        pipeline.add(new CropProcessor());

        if (config.optimizePaths()) {
            pipeline.add(new PathOptimizeProcessor());
        }

        for (Processor p : pipeline) {
            p.process(doc, config);
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(config.outputPath()));
            transformer.transform(source, result);
            System.out.println("Success: " + config.outputPath());

            if (config.printStats()) {
                org.trostheide.svgtoolbox.core.SvgStatistics.Stats stats = org.trostheide.svgtoolbox.core.SvgStatistics
                        .analyze(doc);
                System.out.println("--- Statistics ---");
                System.out.println("Elements: " + stats.elementCount());
                System.out.println(String.format("Total Length: %.2f meters", stats.totalLengthMeters()));
            }
        } catch (TransformerException e) {
            throw new IOException("Error saving SVG", e);
        }
    }
}