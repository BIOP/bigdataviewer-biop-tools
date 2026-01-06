package ch.epfl.biop.labkit;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.SourceAndConverter;
import net.imglib2.Interval;
import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imagej.ImgPlus;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.labkit.ui.bdv.BdvShowable;
import sc.fiji.labkit.ui.inputimage.InputImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of Labkit's {@link InputImage} interface for an array of {@link SourceAndConverter}.
 * <p>
 * Each SourceAndConverter is treated as a separate channel. The sources must have compatible
 * dimensions (X, Y, Z) and pixel types.
 * </p>
 *
 * @param <T> the pixel type of the sources
 */
public class SourcesInputImage<T extends NumericType<T> & NativeType<T>> implements InputImage {

    private final SourceAndConverter<T>[] sources;
    private final int resolutionLevel;
    private final ImgPlus<T> imgPlus;
    private final BdvShowable bdvShowable;
    private String defaultLabelingFilename;

    /**
     * Creates a new SourceAndConverterInputImage from an array of sources.
     *
     * @param sources the array of SourceAndConverter to wrap
     * @param resolutionLevel the resolution level to use (0 for full resolution)
     */
    public SourcesInputImage(SourceAndConverter<T>[] sources, int resolutionLevel) {
        if (sources == null || sources.length == 0) {
            throw new IllegalArgumentException("Sources array cannot be null or empty");
        }
        this.sources = sources;
        this.resolutionLevel = resolutionLevel;
        this.imgPlus = SourcesToImgPlus.wrap(sources, sources[0].getSpimSource().getName(), 0);
        this.bdvShowable = createBdvShowable();
        this.defaultLabelingFilename = sources[0].getSpimSource().getName() + ".labeling";
    }

    /**
     * Creates an ImgPlus from the sources by stacking them as channels.
     */
    /*private ImgPlus<T> createImgPlus() {
        // Get the first source to determine dimensions and type
        Source<T> firstSource = sources[0].getSpimSource();
        RandomAccessibleInterval<T> firstRai = firstSource.getSource(0, resolutionLevel);

        // If single source, create ImgPlus directly
        if (sources.length == 1) {
            return wrapAsImgPlus(firstRai, firstSource.getName());
        }

        // Stack multiple sources as channels
        List<RandomAccessibleInterval<T>> rais = Arrays.stream(sources)
                .map(sac -> sac.getSpimSource().getSource(0, resolutionLevel))
                .collect(Collectors.toList());

        // Stack along a new dimension (channel dimension)
        RandomAccessibleInterval<T> stacked = Views.stack(rais);

        return wrapAsImgPlus(stacked, firstSource.getName());
    }

    private ImgPlus<T> wrapAsImgPlus(RandomAccessibleInterval<T> rai, String name) {
        // Get the transformation to extract voxel sizes
        AffineTransform3D transform = new AffineTransform3D();
        sources[0].getSpimSource().getSourceTransform(0, resolutionLevel, transform);

        // Calculate voxel sizes from the transform
        double[] voxelSizes = new double[3];
        for (int d = 0; d < 3; d++) {
            double sum = 0;
            for (int i = 0; i < 3; i++) {
                double val = transform.get(d, i);
                sum += val * val;
            }
            voxelSizes[d] = Math.sqrt(sum);
        }

        // Get unit if available
        String unit = "pixel";
        if (sources[0].getSpimSource().getVoxelDimensions() != null) {
            String srcUnit = sources[0].getSpimSource().getVoxelDimensions().unit();
            if (srcUnit != null) {
                unit = srcUnit;
            }
        }

        // Wrap as Img
        Img<T> img = ImgView.wrap(rai);

        // Determine axes based on dimensions
        int nDims = rai.numDimensions();
        AxisType[] axes = new AxisType[nDims];
        double[] calibration = new double[nDims];

        if (nDims >= 1) {
            axes[0] = Axes.X;
            calibration[0] = voxelSizes[0];
        }
        if (nDims >= 2) {
            axes[1] = Axes.Y;
            calibration[1] = voxelSizes[1];
        }
        if (nDims >= 3) {
            axes[2] = Axes.Z;
            calibration[2] = voxelSizes[2];
        }
        if (nDims >= 4) {
            // If we stacked channels, the 4th dimension is channel
            axes[3] = Axes.CHANNEL;
            calibration[3] = 1;
        }
        if (nDims >= 5) {
            axes[4] = Axes.TIME;
            calibration[4] = 1;
        }

        ImgPlus<T> imgPlus = new ImgPlus<>(img, name, axes, calibration);

        return imgPlus;
    }*/

    /**
     * Creates a BdvShowable from the sources.
     * Wraps the ImgPlus so all channels are displayed.
     */
    private BdvShowable createBdvShowable() {
        // Wrap the ImgPlus to display all channels
        return new SourcesShowable<>(sources,0);
    }

    @Override
    public ImgPlus<? extends NumericType<?>> imageForSegmentation() {
        return imgPlus;
    }

    @Override
    public BdvShowable showable() {
        return bdvShowable;
    }

    @Override
    public String getDefaultLabelingFilename() {
        return defaultLabelingFilename;
    }

    /**
     * Sets the default labeling filename.
     *
     * @param filename the filename to use for labeling output
     */
    public void setDefaultLabelingFilename(String filename) {
        this.defaultLabelingFilename = filename;
    }

    /**
     * Returns the wrapped sources.
     *
     * @return the array of SourceAndConverter
     */
    public SourceAndConverter<T>[] getSources() {
        return sources;
    }

    /**
     * Returns the resolution level used.
     *
     * @return the resolution level
     */
    public int getResolutionLevel() {
        return resolutionLevel;
    }

    public static class SourcesShowable<U> implements BdvShowable {
        final List<SourceAndConverter<U>> sources;
        final int numTimepoints;
        final int resolutionLevel;

        SourcesShowable(SourceAndConverter[] sources, int resolutionLevel) {
            this.sources = new ArrayList();
            for (SourceAndConverter source: sources) {
                this.sources.add(source);
            }
            numTimepoints = SourceAndConverterHelper.getMaxTimepoint(sources)+1;
            this.resolutionLevel = resolutionLevel;
        }

        @Override
        public Interval interval() {
            return sources.get(0).getSpimSource().getSource(0, resolutionLevel);
        }

        @Override
        public AffineTransform3D transformation() {
            AffineTransform3D transform = new AffineTransform3D();
            sources.get(0).getSpimSource().getSourceTransform(0, resolutionLevel, transform);
            return transform;
        }

        @Override
        public BdvStackSource<U> show(String s, BdvOptions bdvOptions) {
            return BdvFunctions.show(sources, numTimepoints, bdvOptions);
        }
    }

}
