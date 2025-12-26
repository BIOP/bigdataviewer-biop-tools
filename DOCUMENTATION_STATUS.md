# SciJava Command Documentation Status

This document tracks the documentation status of all SciJava commands (`@Plugin` annotated classes) in the bigdataviewer-biop-tools project.

**Total Commands Found: ~70**
**Fully Documented: ~65** (with @Plugin description + @Parameter labels/descriptions)
**Partially Documented: ~3** (has @Plugin description but may need review)
**Needs Documentation: ~2**

---

## Fully Documented Commands (~65)

These commands have both `@Plugin(description=...)` and `@Parameter(label=..., description=...)` for all user-facing parameters.

### SPIM Data Commands (9)
| File | Description |
|------|-------------|
| `spimdata/LLS7OpenDatasetCommand.java` | Opens a Zeiss LLS7 dataset with live deskewing |
| `spimdata/CreateCZIDatasetCommand.java` | Creates BigStitcher-compatible XML from CZI |
| `spimdata/FuseBigStitcherDatasetIntoOMETiffCommand.java` | Fuses BigStitcher dataset to OME-TIFF |
| `spimdata/OpenOperettaDatasetCommand.java` | Opens PerkinElmer Operetta dataset |
| `spimdata/LLS7CropCommand.java` | Crops 3D region from LLS7 sources |
| `spimdata/ReorderDatasetCommand.java` | Reorders LIF dataset (legacy) |
| `spimdata/DatasetToBigStitcherDatasetCommand.java` | Converts BDV to BigStitcher format |
| `spimdata/RemoveDisplaySettingsCommand.java` | Removes display settings from BDV dataset |
| `spimdata/RemoveEntitiesCommand.java` | Removes entities from BDV dataset |
| `spimdata/SourceFromImagePlusCommand.java` | Converts ImagePlus to BDV dataset |
| `spimdata/OpenImarisCommand.java` | Opens Imaris .ims files |

### BDV Commands (8)
| File | Description |
|------|-------------|
| `bdv/userdefinedregion/GetUserPointsCommand.java` | Interactive point selection in BDV |
| `bdv/userdefinedregion/GetUserRectangleCommand.java` | Interactive rectangle selection in BDV |
| `bdv/userdefinedregion/BoxSelectorCommand.java` | Interactive 3D box selection in BDV |
| `bdv/BdvViewToImagePlusExportCommand.java` | Exports current BDV view to ImagePlus |
| `bdv/BasicBdvViewToImagePlusExportCommand.java` | Simple BDV view to ImagePlus export |
| `bdv/OverviewerCommand.java` | Creates overview visualization of sources |
| `bdv/ShowGridBdvCommand.java` | Displays sources in a grid layout |
| `bdv/CurrentBdvSetTimepointsNumberCommand.java` | Sets timepoint count in BDV slider |

### PairRegistration Commands - Warpy Workflow (12)
| File | Description |
|------|-------------|
| `registration/PairRegistrationCreateCommand.java` | Creates a new registration pair from fixed and moving sources |
| `registration/PairRegistrationAddGUICommand.java` | Opens a BDV window with controls for performing registrations |
| `registration/PairRegistrationDeleteCommand.java` | Removes a registration pair from memory |
| `registration/PairRegistrationCenterCommand.java` | Applies a translation to center moving sources over fixed sources |
| `registration/PairRegistrationSift2DAffineCommand.java` | Automatic 2D affine registration using SIFT feature matching |
| `registration/PairRegistrationElastix2DAffineCommand.java` | Automatic 2D affine registration using Elastix |
| `registration/PairRegistrationElastix2DSplineCommand.java` | Automatic 2D B-spline deformable registration using Elastix |
| `registration/PairRegistrationBigWarp2DSplineCommand.java` | Interactive manual landmark-based spline registration |
| `registration/PairRegistrationEditLastRegistrationCommand.java` | Re-opens the last registration step for editing |
| `registration/PairRegistrationRemoveLastRegistrationCommand.java` | Removes the last registration step |
| `registration/PairRegistrationExportToOMETIFFCommand.java` | Exports registered images as pyramidal OME-TIFF |
| `registration/PairRegistrationExportToQuPathCommand.java` | Exports registration transforms to QuPath project |

### Source Export Commands (2)
| File | Description |
|------|-------------|
| `source/ExportToImagePlusCommand.java` | Exports sources to ImagePlus ignoring spatial location |
| `source/ExportToMultipleImagePlusCommand.java` | Exports sources to multiple ImagePlus respecting locations |

### Source Manipulation Commands (8)
| File | Description |
|------|-------------|
| `source/FilterSourcesByNameCommand.java` | Filters sources by name pattern |
| `source/SourceTimeShiftCommand.java` | Creates time-shifted source |
| `source/SourcesPyramidizerCommand.java` | Generates pyramid levels for sources |
| `source/SourcesMakeModelCommand.java` | Creates model source spanning multiple sources |
| `source/GetVoronoiEllipseSampleCommand.java` | Creates sample Voronoi ellipse source |
| `source/SliceSourceCommand.java` | Resamples sources to oblique slice |
| `source/SourceSetAlphaCommand.java` | Sets L1 alpha blending for sources |
| `source/SourcesFuserAndResamplerCommand.java` | Fuses and resamples sources |

### Source Deconvolution Commands (1)
| File | Description |
|------|-------------|
| `source/deconvolve/SourcesDeconvolverCommand.java` | GPU-accelerated Richardson-Lucy deconvolution |

### Source Registration Commands (11+ including abstract classes)
| File | Description |
|------|-------------|
| `source/register/AffineTransformCreatorCommand.java` | Creates affine transform from matrix |
| `source/register/Elastix2DSplineRegisterCommand.java` | 2D B-spline registration using Elastix |
| `source/register/Sift2DAffineRegisterCommand.java` | 2D affine registration using SIFT |
| `source/register/SourcesAffineTransformCommand.java` | Applies affine transform to sources |
| `source/register/SourcesRealTransformCommand.java` | Applies real transform to sources |
| `source/register/SelectSourcesForRegistrationCommand.java` | Abstract base for registration source selection |
| `source/register/Abstract2DRegistrationInRectangleCommand.java` | Abstract base for 2D ROI registration |
| `source/register/AbstractElastix2DRegistrationInRectangleCommand.java` | Abstract base for Elastix ROI registration |
| `source/register/WarpyEditRegistrationCommand.java` | Edit existing Warpy registration |
| `source/register/WarpyExportRegisteredImageCommand.java` | Export Warpy registered image to OME-TIFF |
| `source/register/WarpyMultiscaleRegisterCommand.java` | Automated multiscale Warpy registration |
| `source/register/WarpyRegisterCommand.java` | Interactive Warpy registration wizard |

### Transform Commands (13)
| File | Description |
|------|-------------|
| `transform/RemoveZOffsetCommand.java` | Removes Z offset from sources |
| `transform/SourcesRecenterCommand.java` | Recenters sources to specified coordinates |
| `transform/Rotation3DTransformCommand.java` | Interactive 3D rotation transform |
| `transform/EditSourcesWarpingCommand.java` | Edit warping transform with BigWarp |
| `transform/Rot3DReSampleCommand.java` | 3D resampling using ROI points |
| `transform/DisplayEllipseFromTransformCommand.java` | Creates ellipsoid visualization source |
| `transform/Elliptic3DTransformCreatorCommand.java` | Creates new elliptical 3D transform |
| `transform/Elliptic3DTransformerCommand.java` | Applies elliptical transform to sources |
| `transform/Elliptic3DTransformExporterCommand.java` | Exports elliptical transform to JSON |
| `transform/Elliptic3DTransformImporterCommand.java` | Imports elliptical transform from JSON |
| `transform/Optimize3DEllipticalTransformCommand.java` | Optimizes elliptical transform parameters |
| `transform/ExportEllipticProjection.java` | Exports elliptic projection to ImagePlus |
| `transform/EasyExportEllipticProjection.java` | Interactive elliptic projection export |

---

## Partially Documented Commands (~3)

These commands may need review for complete documentation.

| File | Notes |
|------|-------|
| `source/register/Elastix2DAffineRegisterCommand.java` | Inherits from abstract, check all params |
| `source/register/Elastix2DSparsePointsRegisterCommand.java` | Check all params documented |
| `source/register/MultiscaleRegisterCommand.java` | Check all params documented |

---

## Commands Needing Documentation (~2)

These commands may still need documentation review.

| File | Notes |
|------|-------|
| `source/register/RegisterWholeSlideScans2DCommand.java` | Complex command, needs review |
| `source/register/Wizard2DWholeScanRegisterCommand.java` | Complex wizard command |

---

## Documentation Guidelines

When documenting commands, add:

1. **@Plugin annotation** - Add `description` attribute:
   ```java
   @Plugin(type = Command.class,
           menuPath = "...",
           description = "Brief description of what the command does")
   ```

2. **@Parameter annotations** - Add `label` and `description` for user-facing parameters:
   ```java
   @Parameter(label = "Select Source(s)",
              description = "The source(s) to process")
   SourceAndConverter<?>[] sources;
   ```

3. **Service parameters** - No documentation needed (not user-facing):
   ```java
   @Parameter
   SourceAndConverterService sacService;  // No label/description needed
   ```

### Style Guidelines
- Labels: Use Title Case, 2-4 words
- Descriptions: Complete sentences, user-friendly language
- Avoid jargon: Use "image source" instead of "SourceAndConverter"
- Use "world coordinates units" instead of "physical units"

---

*Last updated: 2025-12-26*