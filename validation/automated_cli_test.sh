#!/bin/bash
# automated_cli_test.sh
# This script verifies that the SVGToolBox CLI processes an SVG correctly,
# specifically ensuring that Path Optimization (--optimize) works without crashing.

set -e # Exit immediately if a command exits with a non-zero status
echo "Starting Automated CLI Validation..."

# Get absolute path to the SVGToolBox project root
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

JAR_FILE="$PROJECT_ROOT/target/svgtoolbox-1.0-SNAPSHOT.jar"
INPUT_FILE="$PROJECT_ROOT/example.svg"
OUTPUT_FILE="$PROJECT_ROOT/validation/cli_test_output.svg"

# Ensure the project is built
if [ ! -f "$JAR_FILE" ]; then
    echo "JAR not found at $JAR_FILE. Please build the project from the root directory first: mvn clean package"
    exit 1
fi

# Run the command with Hatching and Optimization enabled
echo "Running SVGToolBox CLI with --optimize..."
java -jar "$JAR_FILE" \
    --input "$INPUT_FILE" \
    --output "$OUTPUT_FILE" \
    --stroke-width 1.0 \
    --hatch \
    --optimize

echo ""
# Check if output file was created and is not empty
if [ -s "$OUTPUT_FILE" ]; then
    echo "✅ SUCCESS: Output file '$OUTPUT_FILE' generated successfully."
    echo "Verification complete. Path optimization and Batik parsing are working correctly via CLI."
else
    echo "❌ ERROR: Output file was not generated or is empty."
    exit 1
fi
