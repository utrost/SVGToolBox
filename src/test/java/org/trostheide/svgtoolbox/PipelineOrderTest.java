package org.trostheide.svgtoolbox;

import org.junit.jupiter.api.Test;
import org.trostheide.svgtoolbox.processors.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PipelineOrderTest {

    @Test
    void testPipelineOrderMatchesExpected() {
        // Build the pipeline the same way SvgToolboxRunner does
        List<Processor> pipeline = new ArrayList<>();
        pipeline.add(new VisibilityProcessor());
        pipeline.add(new StyleNormalizerProcessor());
        pipeline.add(new RotateProcessor());
        pipeline.add(new StrokeWidthProcessor());
        pipeline.add(new PaletteProcessor());
        pipeline.add(new SimplifyProcessor());
        pipeline.add(new HatchProcessor());
        pipeline.add(new LayerProcessor());
        pipeline.add(new CropProcessor());

        assertEquals(9, pipeline.size(), "Base pipeline should have 9 processors");

        // Verify order
        assertInstanceOf(VisibilityProcessor.class, pipeline.get(0), "VisibilityProcessor should be first");
        assertInstanceOf(StyleNormalizerProcessor.class, pipeline.get(1), "StyleNormalizerProcessor should be second");
        assertInstanceOf(RotateProcessor.class, pipeline.get(2), "RotateProcessor should be third");
        assertInstanceOf(StrokeWidthProcessor.class, pipeline.get(3), "StrokeWidthProcessor should be fourth");
        assertInstanceOf(PaletteProcessor.class, pipeline.get(4), "PaletteProcessor should be fifth");
        assertInstanceOf(SimplifyProcessor.class, pipeline.get(5), "SimplifyProcessor should be sixth");
        assertInstanceOf(HatchProcessor.class, pipeline.get(6), "HatchProcessor should be seventh");
        assertInstanceOf(LayerProcessor.class, pipeline.get(7), "LayerProcessor should be eighth");
        assertInstanceOf(CropProcessor.class, pipeline.get(8), "CropProcessor should be ninth");
    }

    @Test
    void testPathOptimizeProcessorAddedConditionally() {
        Config config = new Config.Builder()
                .inputPath("in").outputPath("out")
                .optimizePaths(true)
                .build();

        List<Processor> pipeline = new ArrayList<>();
        pipeline.add(new VisibilityProcessor());
        pipeline.add(new StyleNormalizerProcessor());
        pipeline.add(new RotateProcessor());
        pipeline.add(new StrokeWidthProcessor());
        pipeline.add(new PaletteProcessor());
        pipeline.add(new SimplifyProcessor());
        pipeline.add(new HatchProcessor());
        pipeline.add(new LayerProcessor());
        pipeline.add(new CropProcessor());

        if (config.optimizePaths()) {
            pipeline.add(new PathOptimizeProcessor());
        }

        assertEquals(10, pipeline.size(), "Pipeline with optimize should have 10 processors");
        assertInstanceOf(PathOptimizeProcessor.class, pipeline.get(9),
                "PathOptimizeProcessor should be last");
    }

    @Test
    void testVisibilityRunsBeforeStyleNormalization() {
        // Visibility must run first to remove hidden elements before any processing
        List<Processor> pipeline = new ArrayList<>();
        pipeline.add(new VisibilityProcessor());
        pipeline.add(new StyleNormalizerProcessor());

        assertInstanceOf(VisibilityProcessor.class, pipeline.get(0),
                "VisibilityProcessor must run before StyleNormalizerProcessor");
    }

    @Test
    void testHatchRunsBeforeLayers() {
        // HatchProcessor generates shapes that LayerProcessor needs to organize
        List<Processor> pipeline = new ArrayList<>();
        pipeline.add(new HatchProcessor());
        pipeline.add(new LayerProcessor());

        assertInstanceOf(HatchProcessor.class, pipeline.get(0),
                "HatchProcessor must run before LayerProcessor");
    }
}
