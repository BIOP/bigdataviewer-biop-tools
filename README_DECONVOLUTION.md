# Lazy Multi-GPU Tiled Deconvolution for ImageJ/Fiji

This repository also contains a high-performance ImageJ plugin for GPU-accelerated Richardson-Lucy deconvolution of large microscopy images using [BigDataViewer-Playground](https://github.com/bigdataviewer/bigdataviewer-playground) and [CLIJ2](https://github.com/clij/clij2), built on top of [CLIJ-FFT](https://github.com/clij/clij2-fft/).


## Overview

The deconvolution commands plugin enables **lazy, tiled Richardson-Lucy deconvolution** of BigDataViewer sources directly on GPUs. It's designed to handle large datasets that don't fit in memory by processing them in configurable tiles with overlap.

You can also utilise Multi-GPU (and split them as needed).

### Key Features

- **GPU Acceleration**: Leverages CLIJ2 for fast deconvolution on the GPU
- **Tiled Processing**: Handles arbitrarily large images through lazy, tiled computation
- **BigDataViewer Integration**: Works with BDV sources
- **Flexible Output**: Keep original pixel type or output as float
- **Multi-threaded**: Parallel processing of tiles for maximum performance
- **Non-Circulant Option**: Support for non-circulant boundary conditions

## Installation

### Prerequisites

- [Fiji](https://fiji.sc/)
- One or several OpenCL capable devices (GPU, but not only)

### Using the Update Site

1. Open Fiji
2. Go to `Help > Update...`
3. Click `Manage Update Sites`
4. Add the following update sites:
    - CLIJ & CLIJ2
    - CLIJ-Deconvolution
    - PTBIOP
    - (Optional: Quick Start CZI Reader for fast CZI reading)
5. Close and restart Fiji

## Usage

### Getting Your Data into BigDataViewer

Before deconvolution, you need to load your images as BDV sources. Here are the main approaches:

#### Option 1: Standard Image Files (TIF, CZI, OME-TIFF, etc.)

For most image formats:
1. Go to `Plugins > BigDataViewer-Playground > BDVDataset > Create BDV Dataset [BioFormats]`
2. Select your image file(s)
3. Configure:
    - **Dataset name**: Choose a name
    - **Unit**: Usually "MICROMETER"
    - **Split RGB channels**: Usually false
    - **Plane origin convention**: "CENTER" or "TOP LEFT"
    - **Auto pyramidize**: true (recommended for large images)

#### Option 2: Lattice Light Sheet 7 (LLS7) Data

For LLS7 CZI files with proper deskewing:
1. Go to `Plugins > BigDataViewer-Playground > BDVDataset > Open LLS7 Dataset`
2. Select your LLS7 CZI file
3. Set **Legacy XY mode**: false (unless you are working aith an older file)

#### Programmatic Example

```groovy
#@Context ctx
// Load standard image file
CommandService cs = ctx.getService(CommandService.class);
SourceAndConverterService ss = ctx.getService(SourceAndConverterService.class);

File imageFile = new File("path/to/image.tif");

AbstractSpimData<?> dataset = (AbstractSpimData<?>) cs.run(
    CreateBdvDatasetBioFormatsCommand.class, true,
    "datasetname", "My_Dataset",
    "unit", "MICROMETER",
    "files", new File[]{imageFile},
    "split_rgb_channels", false,
    "plane_origin_convention", "CENTER",
    "auto_pyramidize", true,
    "disable_memo", false
).get().getOutput("spimdata");

// Get the BDV sources
SourceAndConverter<?>[] sources = ss.getSourceAndConverterFromSpimdata(dataset)
    .toArray(new SourceAndConverter<?>[0]);
```

```groovy
// Load LLS7 CZI file
File lls7File = new File("path/to/lls7.czi");

cs.run(LLS7OpenDatasetCommand.class, true,
    "czi_file", lls7File,
    "legacy_xy_mode", false
).get();

// Retrieve sources by dataset name
String datasetName = FilenameUtils.removeExtension(lls7File.getName());
SourceAndConverter<?>[] sources = ss.getUI()
    .getSourceAndConvertersFromPath(datasetName)
    .toArray(new SourceAndConverter[0]);
```

### Basic Deconvolution Workflow

1. **Load your data** as BDV sources (see above)
2. **Load your PSF** (Point Spread Function) using the same method
3. Run the deconvolution command:
    - Navigate to `Plugins > BigDataViewer-Playground > Sources > Deconvolve sources (Richardson Lucy GPU - Tiled)`
4. Select your sources and PSF
5. Configure the parameters (see below)
6. Click OK

**Important**: The sources are **lazily computed**, meaning the actual deconvolution only happens when you visualize or export the data.

### Parameters

| Parameter | Description | Recommended Values                              |
|-----------|-------------|-------------------------------------------------|
| **Select Source(s)** | Choose one or more BDV sources to deconvolve | -                                               |
| **Select PSF** | Choose the Point Spread Function | Single timepoint, highest resolution            |
| **Output Pixel Type** | Keep original type or convert to Float | Float for best quality, original to save memory |
| **Source Name Suffix** | Suffix added to deconvolved sources | `_deconvolved`                                  |
| **Block Size X/Y/Z** | Tile dimensions in pixels | 128-512 depending on GPU memory                 |
| **Overlap Size** | Overlap between tiles (pixels) | 16-64 (reduce edge artifacts)                   |
| **Number of Iterations** | Richardson-Lucy iterations | 20-100 (more = sharper but slower)              |
| **Non Circulant** | Use non-circulant boundary conditions | true for most cases                             |
| **Regularization Factor** | Prevent over-amplification of noise | 0.001-0.01                                      |
| **Number of Threads** | Parallel tile processing threads | 8-16                                            |

### Complete Example: Deconvolving Lattice Light Sheet Data

```java
// 1. Load LLS7 image data
File lls7File = new File("path/to/Hela-Cell.czi");
cs.run(LLS7OpenDatasetCommand.class, true,
    "czi_file", lls7File,
    "legacy_xy_mode", false
).get();

String datasetName = FilenameUtils.removeExtension(lls7File.getName());
SourceAndConverter<?>[] channels = ss.getUI()
    .getSourceAndConvertersFromPath(datasetName)
    .toArray(new SourceAndConverter[0]);

// 2. Load PSF
File psfFile = new File("path/to/psf-200nm.tif");
AbstractSpimData<?> psfDataset = (AbstractSpimData<?>) cs.run(
    CreateBdvDatasetBioFormatsCommand.class, true,
    "datasetname", "PSF_LLS7_200nm",
    "unit", "MICROMETER",
    "files", new File[]{psfFile},
    "split_rgb_channels", false,
    "plane_origin_convention", "CENTER",
    "auto_pyramidize", false,
    "disable_memo", false
).get().getOutput("spimdata");

SourceAndConverter<?> psf = ss.getSourceAndConverterFromSpimdata(psfDataset)
    .toArray(new SourceAndConverter<?>[0])[0];

// 3. Run deconvolution
ij.command().run(SourcesDeconvolverCommand.class, true,
    "sacs", channels,
    "psf", psf,
    "output_pixel_type", "Float",
    "block_size_x", 512,
    "block_size_y", 512,
    "block_size_z", 128,
    "overlap_size", 64,
    "num_iterations", 40,
    "non_circulant", false,
    "regularization_factor", 0.0001f,
    "n_threads", 4
).get();
```

## How It Works

The plugin implements lazy, cached deconvolution that processes your image in tiles:

1. **Tile Division**: Image divided into overlapping blocks
2. **GPU Processing**: Each tile deconvolved on GPU using Richardson-Lucy algorithm
3. **Caching**: Results cached to avoid recomputation
4. **On-Demand**: Tiles computed only when needed for visualization
5. **Seamless Stitching**: Overlap regions ensure smooth transitions

This approach allows processing of images much larger than available GPU or RAM.

## Performance Tips

### Optimizing Block Size
- **Larger blocks**: Faster overall, but require more GPU memory
- **Smaller blocks**: More overhead, but work with limited GPU memory, and gives faster results for quick evaluation of the result
- Start with 256×256×Z and adjust based on your GPU
- You can also split one or several OpenCL devices, according to instructions in the README of https://github.com/clij/clijx-parallel

### Optimizing Overlap
- **More overlap**: Better edge quality, slower processing
- **Less overlap**: Faster, but may show tile boundaries
- Recommended: 0.5x to 1x of PSF size

### GPU Memory Issues
If you encounter out-of-memory errors:
- Reduce block size
- Close other GPU applications

## Technical Details

### Algorithm
Implements the **Richardson-Lucy** iterative deconvolution algorithm:
- Optimal for Poisson noise (typical in microscopy)
- Preserves positivity
- Can handle regularization

### Implementation
- Built on **CLIJ2** for GPU acceleration via OpenCL
- Uses **ImgLib2** cached cells for lazy processing
- Integrates with **BigDataViewer** for visualization
- Thread-safe multi-resolution support
- Uses GPU pooling for efficient multi-GPU utilization

## Requirements

- Java 8 or higher
- OpenCL capable device(s) - typically NVIDIA or AMD GPUs, but also Intel integrated graphics and MacOS chips
- Sufficient GPU memory for block size + PSF

## Support

- **Issues**: [GitHub Issues](https://github.com/BIOP/ijp-bdv-deconvolution/issues)
- **Forum**: [Image.sc Forum](https://forum.image.sc/)

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the license headers in source files for details.

## Acknowledgments

- Developed at the [BioImaging and Optics Platform (BIOP)](https://biop.epfl.ch), EPFL
- Built on [CLIJ2](https://clij.github.io/) by Robert Haase and
- [clij2-fft](https://github.com/clij/clij2-fft/) by Brian Northan
- Uses [BigDataViewer](https://imagej.net/plugins/bdv/) ecosystem
- Part of the [BigDataViewer-Playground](https://github.com/bigdataviewer/bigdataviewer-playground) suite

## Related Projects

- [bigdataviewer-playground](https://github.com/bigdataviewer/bigdataviewer-playground)
- [CLIJ2](https://github.com/clij/clij2)
- [bigdataviewer-biop-tools](https://github.com/BIOP/bigdataviewer-biop-tools)
