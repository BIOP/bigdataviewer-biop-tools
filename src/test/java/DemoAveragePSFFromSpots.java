import bdv.util.BdvFunctions;
import bdv.util.RandomAccessibleIntervalSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.command.process.deconvolve.AveragePSFFromSpotsCommand;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.command.CommandModule;
import sc.fiji.bdvpg.source.SourceHelper;

import java.io.File;

/**
 * Averages a bead image into an experimental PSF, using the subpixel spot positions of a TrackMate
 * XML file. Unlike the other demos, this one runs on local files rather than on a Zenodo dataset.
 */
public class DemoAveragePSFFromSpots {

    static {
        LegacyInjector.preinit();
    }

    static final String BEADS = "I:\\common\\biochem-scopes\\charlotte\\Nicolas\\beads.tif";
    static final String TRACKMATE = "I:\\common\\biochem-scopes\\charlotte\\Nicolas\\beads.xml";

    public static void main(final String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        demoAveragePSF(ij);
    }

    public static void demoAveragePSF(ImageJ ij) throws Exception {
        File beadsFile = new File(BEADS);
        File trackMateFile = new File(TRACKMATE);
        if (!beadsFile.exists() || !trackMateFile.exists()) {
            IJ.log("Demo files not found:\n" + beadsFile + "\n" + trackMateFile);
            return;
        }

        SourceAndConverter<?> beads = openAsSource(beadsFile);
        BdvFunctions.show(beads.getSpimSource());

        CommandModule module = ij.command().run(
                AveragePSFFromSpotsCommand.class, true,
                "beads", new SourceAndConverter[]{beads},
                "trackmate_file", trackMateFile,
                "visible_spots_only", true,
                "psf_size_x", 64,
                "psf_size_y", 64,
                "psf_size_z", 64,
                // 0 means "same voxel size as the input"; the beads sit at random subpixel offsets,
                // so halving the voxel size here is a legitimate way to oversample the PSF.
                "voxel_size_x", 0d,
                "voxel_size_y", 0d,
                "voxel_size_z", 0d,
                "background_percentile", 50d,
                "n_threads", 8,
                "name", "average_psf"
        ).get();

        SourceAndConverter<?>[] psfs = (SourceAndConverter[]) module.getOutput("psf_out");
        for (SourceAndConverter<?> psf : psfs) {
            BdvFunctions.show(psf.getSpimSource());
        }
    }

    /**
     * Opens a calibrated 3D TIFF as a source whose world coordinates are
     * {@code pixelIndex * voxelSize} - the very convention TrackMate uses for POSITION_X/Y/Z, so the
     * spot positions of the XML land exactly where they should.
     */
    static SourceAndConverter<?> openAsSource(File file) {
        ImagePlus imp = IJ.openImage(file.getAbsolutePath());
        Calibration cal = imp.getCalibration();
        RandomAccessibleInterval<UnsignedShortType> rai = ImageJFunctions.wrapShort(imp);

        AffineTransform3D transform = new AffineTransform3D();
        transform.scale(cal.pixelWidth, cal.pixelHeight, cal.pixelDepth);

        Source<UnsignedShortType> source = new RandomAccessibleIntervalSource<>(
                rai, new UnsignedShortType(), transform, imp.getTitle());
        return SourceHelper.createSourceAndConverter(source);
    }
}
