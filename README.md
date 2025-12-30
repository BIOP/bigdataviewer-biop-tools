# BigDataViewer BIOP Tools

[![](https://github.com/BIOP/bigdataviewer-biop-tools/actions/workflows/build-main.yml/badge.svg)](https://github.com/BIOP/bigdataviewer-biop-tools/actions/workflows/build-main.yml)
[![Maven Scijava Version](https://img.shields.io/github/v/tag/BIOP/bigdataviewer-biop-tools?label=Version-[Maven%20Scijava])](https://maven.scijava.org/#browse/browse:releases:ch%2Fepfl%2Fbiop%2Fbigdataviewer-biop-tools)

A Fiji plugin package providing tools for [BigDataViewer](https://imagej.net/plugins/bdv/), including image fusion, GPU-accelerated deconvolution, registration, and data processing. Developed by [BIOP](https://www.epfl.ch/research/facilities/ptbiop/) (BioImaging and Optics Platform) at EPFL.

## Features

### Data Import
- **Multi-format support**: Open CZI (Zeiss), LIF (Leica), Operetta (PerkinElmer), Imaris (.ims), and standard ImagePlus
- **Zeiss LLS7**: Live deskewing for lattice light-sheet data
- **BigStitcher integration**: Convert and fuse BigStitcher datasets to OME-TIFF

### Registration
- **Warpy workflow**: Interactive registration for QuPath projects with landmark-based alignment
- **Automated registration**: SIFT and Elastix-based affine and B-spline registration
- **Multiscale registration**: Coarse-to-fine registration across resolution levels

### Image Processing
- **GPU Deconvolution**: Lazy, tiled Richardson-Lucy deconvolution using CLIJ2 (see [Deconvolution README](README_DECONVOLUTION.md))
- **Alpha blending**: Layer-based compositing with multiple blending modes
- **Source fusion**: Block-based fusion with bounded cache for large datasets

### Transforms
- **3D transforms**: Rotation, recentering, Z-offset removal
- **Elliptical projection**: Spherical/ellipsoidal coordinate transforms for curved surfaces
- **Oblique slicing**: Resample sources along arbitrary view orientations

## Installation

Enable the **PTBIOP** update site in Fiji:

1. `Help > Update... > Manage Update Sites`
2. Check **PTBIOP**
3. Close and restart Fiji

Commands appear under `Plugins > BigDataViewer > BigDataViewer-Playground`.

## Dependencies

This package builds on:
- [bigdataviewer-playground](https://github.com/bigdataviewer/bigdataviewer-playground) - Core BDV infrastructure
- [bigdataviewer-core](https://github.com/bigdataviewer/bigdataviewer-core) - BigDataViewer library
- [CLIJ2](https://clij.github.io/) - GPU acceleration (OpenCL)

## Related Projects

- [QuPath](https://qupath.github.io/) - Warpy registration workflow integration
- [BigStitcher](https://imagej.net/plugins/bigstitcher/) - Tile stitching and fusion
- [Elastix](https://elastix.lumc.nl/) - Deformable image registration
