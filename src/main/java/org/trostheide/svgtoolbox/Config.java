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
        HatchStyle globalStyle,
        Map<String, HatchStyle> overrides,
        List<Color> noHatchColors,
        double minHatchArea,
        double simplifyTolerance // New field: > 0 enables simplification
) {}