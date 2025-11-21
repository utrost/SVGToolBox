# **SVGToolBox**

**SVGToolBox** is a robust, modular Java CLI utility engineered to bridge the gap between generative vector art (e.g., outputs from the "Primitive" engine) and physical pen plotters (like the AxiDraw).  
While standard SVG generators create files for screens (using solid fills and infinite colors), **SVGToolBox** adapts these files for the physical world. It quantizes colors to match your pen collection, converts solid fills into hatch patterns, and organizes the output into layers for efficient plotting.  
graph TD  
Input\[Input SVG\] \--\> Loader  
Loader \--\> DOM\[SVG DOM\]  
DOM \--\> P1\[StrokeWidthProcessor\]  
P1 \--\> P2\[PaletteProcessor\]  
P2 \--\> P3\[SimplifyProcessor\]  
P3 \--\> P4\[HatchProcessor\]  
P4 \--\> P5\[LayerProcessor\]  
P5 \--\> Output\[Output SVG\]

    subgraph "Pipeline"  
    P1(Normalize Strokes)  
    P2(Quantize Colors)  
    P3(Simplify Paths)  
    P4(Bake Geometry & Hatch)  
    P5(Flatten & Layer)  
    end

## **1\. Architectural Overview**

SVGToolBox is built on the **Pipeline Pattern**, ensuring a clear separation of concerns and easy extensibility. The architecture leverages **Apache Batik** for industry-standard SVG parsing and **Java 17** for modern language features.

### **1.1 The Pipeline**

The application lifecycle is linear and stateless:

1. **Initialization:** The SvgToolboxRunner parses CLI arguments and builds a configuration record.
2. **Loading:** The input SVG is parsed into a W3C DOM Object using Batik's SAXSVGDocumentFactory.
3. **Processing:** The DOM is passed sequentially through a list of Processor implementations. Each processor modifies the DOM in-place.
    * StrokeWidthProcessor: Standardizes line weights.
    * PaletteProcessor: Maps colors to physical pens.
    * SimplifyProcessor: Reduces path complexity (Ramer-Douglas-Peucker).
    * HatchProcessor: Converts fills to lines using geometric baking (Scanline).
    * LayerProcessor: Flattens groups and organizes into Inkscape layers.
4. **Serialization:** The modified DOM is written back to disk as a standard SVG file.

### **1.2 Key Components**

* **Processor Interface:** The contract for all modules.  
  public interface Processor {  
  void process(Document doc, Config config);  
  }

* **Config Record:** An immutable data carrier that propagates CLI settings (palettes, angles, gaps) to the processors.

## **2\. Installation & Build**

### **Prerequisites**

* **Java 17** (or higher)
* **Maven 3.8+**

### **Building from Source**

To compile the project and generate a standalone Uber-JAR (containing all dependencies):  
mvn clean package

The executable will be located at:  
target/svgtoolbox-1.0-SNAPSHOT.jar

### **Installation Script**

For convenience, run the included install script to build the project and create a global alias:  
./install.sh

You can now run svgtoolbox from any directory.

## **3\. Functional Modules (Processors)**

### **3.1 Stroke Normalizer (StrokeWidthProcessor)**

Ensures that every path in the SVG has a stroke width corresponding to your physical pen tip. This is crucial for previewing how the plot will actually look.

### **3.2 Color Quantizer (PaletteProcessor)**

Mapping digital colors to physical pens.

* **Logic:** It iterates through every shape and calculates the **Euclidean Distance** between the shape's color and your allowed palette.
* **Result:** A shape with fill \#A00000 might be snapped to \#FF0000 (Red Marker) if that is the closest available pen.

### **3.3 Path Simplifier (SimplifyProcessor)**

Optimizes the input geometry before hatching.

* **Algorithm:** Uses Ramer-Douglas-Peucker (RDP) to reduce the number of points in a path.
* **Benefit:** Removes noise/jitter from generative algorithms, resulting in cleaner hatch lines and faster plotting.

### **3.4 Hatching Engine (HatchProcessor)**

The core of the tool. It converts filled areas into line patterns using a **Scanline Algorithm**.

* **World-Space Baking:** Calculates the global transform of every shape to generate lines in absolute coordinates. This fixes scaling/positioning issues caused by nested groups.
* **Geometric Intersection:** Instead of using SVG clipPath, it calculates the exact start/end points of lines intersecting the shape.
* **Cross-Hatching:** Supports generating a second pass of lines at 90° to the first.
* **Filtering:** Can ignore shapes based on color (creating outlines) or size (ignoring "dust").

### **3.5 Layer Organizer (LayerProcessor)**

Prepares the file for multi-pen plotting.

* It inspects the stroke color of every element (handling inheritance).
* It moves elements out of deep nesting and into flat top-level groups with inkscape:groupmode="layer".
* **Auto-Fit:** It recalculates the viewBox of the root SVG to perfectly frame the generated content, ensuring visibility.

## **4\. CLI Reference**

**Syntax:**  
svgtoolbox \-i \<input\> \-o \<output\> \[options\]

### **Core Options**

| Flag | Long Flag | Argument | Description |
| :---- | :---- | :---- | :---- |
| \-i | \--input | File Path | **Required.** Source SVG file. |
| \-o | \--output | File Path | **Required.** Destination path. |
| \-p | \--palette | hex,hex... | Comma-separated list of allowed pen colors (e.g., \#000000,\#FF0000). |
| \-w | \--stroke-width | Float | Global stroke width in pixels (e.g., 1.0). Default: 1.0. |
| \-h | \--hatch | None | Enable the hatching engine. |

### **Hatching Control**

| Flag | Long Flag | Argument | Description |
| :---- | :---- | :---- | :---- |
|  | \--hatch-angle | Degrees | **Global** angle for hatch lines (Default: 45.0). |
|  | \--hatch-gap | Float | **Global** spacing between lines (Default: 5.0). |
|  | \--no-hatch | hex,hex... | Colors to skip hatching. These will remain as outlines (if stroke exists) or invisible. |
|  | \--min-area | Float | Minimum area (px²) required to hatch a shape. Useful for filtering noise. Default: 100\. |
|  | \--simplify | Float | RDP Tolerance for path simplification (e.g., 0.5 to 2.0). 0 \= Disabled. |

### **Advanced Styling**

| Flag | Long Flag | Argument | Description |
| :---- | :---- | :---- | :---- |
| \-S | \--style | String | Per-color overrides. See syntax below. |

Style Syntax:  
HEX\_COLOR : ANGLE : GAP : CROSS\_HATCH\_BOOL  
Multiple overrides are separated by semicolons ;.  
**Example:** \#FF0000:45:5.0:false;\#000000:0:2.0:true

* **Red:** 45° angle, 5px gap, single pass.
* **Black:** 0° angle, 2px gap, **cross-hatch** (grid pattern).

## **5\. Detailed Parameter Guide**

### **Hatching Gap (--hatch-gap or via \--style)**

Controls the **density** of the fill.

* **Value:** Distance between lines in pixels/units.
* **Impact:**
    * **Small Gap (2.0 \- 4.0):** Creates a very dense, dark fill. Can result in "black blobs" on screen or ink bleeding on paper if too tight. Increases file size significantly.
    * **Medium Gap (5.0 \- 8.0):** Standard shading. Lines are distinguishable.
    * **Large Gap (10.0+):** Light, airy texture. Good for background layers or "sketchy" looks.
* **Cross-Hatching Note:** If using cross-hatching, you are drawing 2x the lines. Consider increasing the gap (e.g., if 6.0 looks good for single lines, try 10.0 or 12.0 for a grid to maintain similar optical density).

### **Simplification (--simplify)**

Controls the **smoothness** of the input shapes before hatching.

* **Value:** Tolerance (epsilon) for the Ramer-Douglas-Peucker algorithm.
* **Impact:**
    * **0.0 (Default):** Disabled. Shapes are hatched exactly as input.
    * **0.5 \- 1.0:** Gentle cleanup. Removes redundant points on straight lines. Recommended for most vector art.
    * **2.0 \- 5.0:** Aggressive. Smooth curves become angular polygons. Can be used artistically for a "low poly" or "glitch" aesthetic.
* **Note:** This simplifies the *boundary shape*, not the hatched lines themselves.

### **Minimum Area (--min-area)**

Controls **noise filtering**.

* **Value:** Area in square pixels (px²).
* **Impact:** Any closed shape smaller than this value is ignored (deleted) and not hatched.
* **Usage:**
    * **50 \- 100:** Removes tiny dust specks or single-pixel artifacts from generative processes.
    * **500 \- 1000:** Removes small details (e.g., eyes, buttons), leaving only large masses. useful for creating abstract background layers.

## **6\. Usage Examples**

### **Scenario A: The "Quick Proof"**

Convert a complex generative art piece into a single-color plot (Black pen). Standard 45° hatching.  
svgtoolbox \\  
\-i raw\_output.svg \\  
\-o plot\_ready.svg \\  
\-p "\#000000" \\  
\-w 0.5 \\  
\-h

### **Scenario B: Text & Logos (Hybrid Mode)**

You have a file with a Red Logo and Black Text. You want the logo filled, but the text should remain sharp outlines.  
svgtoolbox \\  
\-i logo\_design.svg \\  
\-o production.svg \\  
\-p "\#FF0000,\#000000" \\  
\-h \\  
\--no-hatch "\#000000"

### **Scenario C: The "Artistic Texture" Workflow**

You are using three pens: Cyan, Magenta, and Black.

* **Cyan:** Light wash (wide gap).
* **Magenta:** Medium tone.
* **Black:** Deep shadow (tight grid).

\<\!-- end list \--\>  
svgtoolbox \\  
\-i input.svg \\  
\-o texture\_study.svg \\  
\-p "\#00FFFF,\#FF00FF,\#000000" \\  
\-h \\  
\-w 0.5 \\  
\--style "\#00FFFF:45:6.0:false;\#FF00FF:135:4.0:false;\#000000:0:8.0:true" \\  
\--simplify 1.0 \\  
\--min-area 100

## **7\. Troubleshooting**

* **Issue:** Output file size is huge (\>2MB).
    * **Reason:** "Baking" geometry (converting fills to thousands of actual line vectors) creates heavy files. Cross-hatching doubles this count. High density (low gap) multiplies it.
    * **Fix:** Increase \--hatch-gap (e.g., 2.0 \-\> 4.0) or use \--min-area 500 to prevent hatching small noise.
* **Issue:** Preview looks like a solid block of color / Moiré patterns.
    * **Reason:** The stroke width is too thick for the gap density, or screen anti-aliasing is struggling with thousands of lines.
    * **Fix:** Use the \-w 0.5 flag to simulate a finer pen tip. Increase gap size.
* **Issue:** Colors aren't separating.
    * **Reason:** Input SVG might use CSS classes (.cls-1 { fill: red }) instead of direct attributes (fill="red").
    * **Fix:** SVGToolBox is optimized for attribute-based SVGs. Use "Export as Presentation Attributes" in your design software.