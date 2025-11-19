package org.trostheide.svgtoolbox;

import java.awt.Color;
import java.util.List;
import java.util.Map;

public record Config(
        String inputPath,
        String outputPath,
        float strokeWidth,
        List<Color> palette,
        boolean enableHatching,
        HatchStyle globalStyle,            // Replaces individual angle/gap fields
        Map<String, HatchStyle> overrides, // New: Map Hex -> Style
        List<Color> noHatchColors,
        double minHatchArea
) {}