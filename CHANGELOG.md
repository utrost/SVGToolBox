# Changelog

All notable changes to SVGToolBox will be documented in this file.

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- GitHub Actions CI pipeline (Maven build + test on Java 17/21)
- CONTRIBUTING.md
- This CHANGELOG

### Changed
- README.md rewritten for clarity and completeness
- .gitignore expanded (IDE files, build artifacts, logs)

### Removed
- Stale files: `benchmark_stress.log`, `dependency-reduced-pom.xml`, `.idea/` directory

## [1.0-SNAPSHOT] — 2024

### Added
- Pipeline architecture with 8 processors
- CLI interface with full hatching control
- Swing GUI with live preview (`start_gui.sh`, `start_gui.bat`)
- Path optimizer (greedy nearest neighbor for pen travel)
- Multiple hatch patterns: linear, cross, zigzag, wave, dot
- Per-color style overrides (`-S` flag)
- Crop and rotate processors
- Style normalizer (CSS → inline attributes)
- SVG statistics output (`--stats`)
- JUnit 5 tests for core processors
