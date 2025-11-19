# **SVGToolBox**

**SVGToolBox** is a robust, modular Java CLI utility engineered to bridge the gap between generative vector art (e.g., outputs from the "Primitive" engine) and physical pen plotters (like the AxiDraw).  
While standard SVG generators create files for screens (using solid fills and infinite colors), **SVGToolBox** adapts these files for the physical world. It quantizes colors to match your pen collection, converts solid fills into hatch patterns, and organizes the output into layers for efficient plotting.

## **1\. Architectural Overview**

SVGToolBox is built on the **Pipeline Pattern**, ensuring a clear separation of concerns and easy extensibility. The architecture leverages **Apache Batik** for industry-standard SVG parsing and **Java 17** for modern language features.

### **1.1 The Pipeline**

The application lifecycle is linear and stateless:

1. **Initialization:** The SvgToolboxRunner parses CLI arguments and builds a configuration record.
2. **Loading:** The input SVG is parsed into a W3C DOM Object using Batik's SAXSVGDocumentFactory.
3. **Processing:** The DOM is passed sequentially through a list of Processor implementations. Each processor modifies the DOM in-place.
    * StrokeWidthProcessor
    * PaletteProcessor
    * HatchProcessor
    * LayerProcessor
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

### **3.3 Hatching Engine (HatchProcessor)**

The core of the tool. It converts filled areas into line patterns.

* **Smart Bounds:** Instead of filling the entire page, it calculates the exact bounding box of each shape (rect, polygon, polyline) to generate lines only where needed.
* **Clipping:** It creates a non-destructive \<clipPath\> referencing the original shape geometry, ensuring lines stop cleanly at the edges.
* **Cross-Hatching:** Supports generating a second pass of lines at 90° to the first for denser textures.
* **Filtering:** Can ignore shapes based on color (creating outlines) or size (ignoring "dust").

### **3.4 Layer Organizer (LayerProcessor)**

Prepares the file for multi-pen plotting.

* It inspects the stroke color of every element.
* It moves elements into grouped \<g\> tags with inkscape:groupmode="layer".
* **Benefit:** When you open the file in Inkscape or Saxi, you can hide "Red" to plot "Black," then swap pens and proceed.

## **4\. CLI Reference**

**Syntax:**  
svgtoolbox \-i \<input\> \-o \<output\> \[options\]

### **Core Options**

| Flag | Long Flag | Argument | Description |
| :---- | :---- | :---- | :---- |
| \-i | \--input | File Path | **Required.** Source SVG file. |
| \-o | \--output | File Path | **Required.** Destination path. |
| \-p | \--palette | hex,hex... | Comma-separated list of allowed pen colors (e.g., \#000000,\#FF0000). |
| \-w | \--stroke-width | Float | Global stroke width in pixels (e.g., 1.0). |
| \-h | \--hatch | None | Enable the hatching engine. |

### **Hatching Control**

| Flag | Long Flag | Argument | Description |
| :---- | :---- | :---- | :---- |
|  | \--hatch-angle | Degrees | **Global** angle for hatch lines (Default: 45.0). |
|  | \--hatch-gap | Float | **Global** spacing between lines (Default: 5.0). |
|  | \--no-hatch | hex,hex... | Colors to skip hatching. These will remain as outlines (if stroke exists) or invisible. |
|  | \--min-area | Float | Minimum area (px²) required to hatch a shape. Useful for filtering noise. Default: 100\. |

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

## **5\. Usage Examples**

### **Scenario A: The "Quick Proof"**

Convert a complex generative art piece into a single-color plot (Black pen). Standard 45° hatching.  
svgtoolbox \\  
\-i raw\_output.svg \\  
\-o plot\_ready.svg \\  
\-p "\#000000" \\  
\-w 1.0 \\  
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

svgtoolbox \\  
\-i input.svg \\  
\-o texture\_study.svg \\  
\-p "\#00FFFF,\#FF00FF,\#000000" \\  
\-h \\  
\--style "\#00FFFF:45:6.0:false;\#FF00FF:135:4.0:false;\#000000:0:2.0:true" \\  
\--min-area 50

## **6\. Troubleshooting**

* **Issue:** Output file size is huge (\>2MB).
    * **Reason:** High-density cross-hatching generates thousands of vectors.
    * **Fix:** Increase \--hatch-gap (e.g., 2.0 \-\> 3.0) or use \--min-area 500 to ignore small details.
* **Issue:** Plotter draws a box around the hatching.
    * **Reason:** Your plotter driver might not support SVG clipPath.
    * **Fix:** Open the file in Inkscape. If it looks correct, the SVG is valid. You may need to select the objects and use Object \-\> Clip \-\> Set (or flatten) depending on your specific driver (e.g., older axicli versions).
* **Issue:** Colors aren't separating.
    * **Reason:** Input SVG might use CSS classes (.cls-1 { fill: red }) instead of direct attributes (fill="red").
    * **Fix:** SVGToolBox is optimized for attribute-based SVGs. Use "Export as Presentation Attributes" in your design software.