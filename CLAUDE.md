# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BigDataViewer-BIOP-Tools is a Fiji plugin package providing tools for BigDataViewer, including image fusion, GPU-accelerated deconvolution, registration, and data processing. Developed by BIOP (BioImaging and Optics Platform) at EPFL.

## Build Commands

```bash
# Full build
mvn clean install

# Build without tests (faster)
mvn clean install -DskipTests

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=DemoDeconvolution
```

## Architecture

### Command-Based Design

The codebase uses **SciJava Commands** as the primary extension mechanism. All user-facing functionality is implemented as commands annotated with `@Plugin(type = Command.class)`. Commands extend `BdvPlaygroundActionCommand` and use `@Parameter` annotations for inputs/outputs.

Key command packages:
- `ch.epfl.biop.scijava.command.bdv/` - BDV visualization commands
- `ch.epfl.biop.scijava.command.source/` - Source manipulation (deconvolution, registration, export)
- `ch.epfl.biop.scijava.command.spimdata/` - SPIM data handling
- `ch.epfl.biop.scijava.command.transform/` - Transform operations

### Source Processing Pipeline

The `SourcesProcessor` interface (`ch.epfl.biop.sourceandconverter.processor`) defines a functional pattern for chaining image operations:

```
SourceAndConverter[] → SourcesProcessor → SourceAndConverter[]
```

Processors are composable, serializable (JSON), and include: affine transforms, channel selection, fusion, identity operations.

### GPU Deconvolution (CLIJ2-based)

`Deconvolver` class implements lazy, tiled Richardson-Lucy deconvolution:
- Uses CLIJ2-FFT for GPU acceleration (OpenCL)
- Processes images in configurable overlapping tiles
- Results cached via ImgLib2's CachedCellImg
- Supports multi-GPU and GPU pooling

### Alpha Blending System

`bdv.util.source.alpha/` and `bdv.util.source.fused/` provide layer-based compositing:
- `AlphaSource` wraps sources with alpha channels
- `LayerAlphaProjectorFactory` handles multi-layer blending
- Blending modes: smooth average, sum, max, min, distance-weighted

### Image Fusion

Block-based fusion with bounded cache (triggers at 50% RAM) for large datasets:
- Addresses BigStitcher memory issues with Soft References
- Pre-filters tiles overlapping each block before fusion
- Exports to OME-TIFF (8/16-bit, RGB) and XML-HDF5

### Registration Framework

`ch.epfl.biop.registration/` provides pair-based registration:
- Elastix integration for affine/spline deformable registration
- SIFT-based registration
- BigWarp transform support
- 2D registration in selected rectangular regions

## Key Dependencies

- **BigDataViewer ecosystem**: `bigdataviewer-core`, `bigdataviewer-playground`, `bigdataviewer-vistools`
- **GPU**: `clij2-fft_` for deconvolution (requires OpenCL devices)
- **Image I/O**: `bigdataviewer-image-loaders`, `quick-start-czi-reader` for CZI files
- **Registration**: `bigwarp_fiji`, `mpicbg_` (SIFT)

## Test Data

Test classes in `src/test/java/` download datasets from Zenodo. Internet connectivity required for running demos like `DemoDeconvolution` or `DemoLLS7`.

## Important Notes

- **Lazy evaluation**: BDV sources compute on-demand; actual processing happens during visualization or export
- **SciJava annotations**: All commands must have proper `@Plugin` and `@Parameter` annotations for Fiji integration
- **JSON serialization**: Processors use JSON adapters - maintain compatibility when modifying processor classes