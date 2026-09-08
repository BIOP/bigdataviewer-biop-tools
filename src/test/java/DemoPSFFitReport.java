import bdv.util.BdvFunctions;
import bdv.util.RandomAccessibleIntervalSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.command.process.deconvolve.PSFFitReportCommand;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import net.imagej.ImageJ;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.CommandModule;
import sc.fiji.bdvpg.source.SourceHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs the alpha PSF fit report on a four-channel set of distilled PSFs. Like
 * {@link DemoAveragePSFFromSpots}, this demo reads local files rather than a Zenodo dataset: the
 * PSFs are 128^3 float volumes of about 8 MB each, which is more than belongs in the test
 * resources of the repository.
 */
public class DemoPSFFitReport {

    static {
        LegacyInjector.preinit();
    }

    static final String PSF_FOLDER =
            "E:\\Université de Genève\\biochem-ipa - Documents\\data\\microscopes\\aumeier-sdc-3i\\psfs\\100x";

    /** In wavelength order, so that the chromatic shift reported against channel 0 reads naturally. */
    static final String[] PSF_FILES = {
            "2026-07-27_100x_psf_c405s.tif",
            "2026-07-27_100x_psf_c488s.tif",
            "2026-07-27_100x_psf_c561s.tif",
            "2026-07-27_100x_psf_c640s.tif"};

    public static void main(final String... args) throws Exception {
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();
        demoPSFFitReport(ij);
    }

    public static void demoPSFFitReport(ImageJ ij) throws Exception {
        List<SourceAndConverter<?>> sources = new ArrayList<>();
        for (String file : PSF_FILES) {
            File psf = new File(PSF_FOLDER, file);
            if (!psf.exists()) {
                IJ.log("Demo file not found: " + psf);
                return;
            }
            sources.add(openAsSource(psf));
        }
        sources.forEach(sac -> BdvFunctions.show(sac.getSpimSource()));

        CommandModule module = ij.command().run(
                PSFFitReportCommand.class, true,
                "psf_sources", sources.toArray(new SourceAndConverter[0]),
                "reference_channel", 0,
                "fit_window", 1.5,
                "level", 0,
                "timepoint", 0,
                "microscope", "Aumeier SDC 3i",
                "objective", "100x",
                "numerical_aperture", 1.45,
                "immersion", "oil",
                "date", "2026-07-27",
                "channel_names", "405, 488, 561, 640"
        ).get();

        IJ.log("---- report ----");
        IJ.log((String) module.getOutput("psf_report"));
    }

    /** Opens a calibrated 3D TIFF as a source whose world coordinates are {@code pixelIndex * voxelSize}. */
    static SourceAndConverter<?> openAsSource(File file) {
        ImagePlus imp = IJ.openImage(file.getAbsolutePath());
        Calibration cal = imp.getCalibration();
        RandomAccessibleInterval<FloatType> rai = ImageJFunctions.wrapFloat(imp);
        IJ.log(imp.getTitle() + ": " + Arrays.toString(rai.dimensionsAsLongArray()) + " voxels of "
                + cal.pixelWidth + " x " + cal.pixelHeight + " x " + cal.pixelDepth + " " + cal.getUnit());

        AffineTransform3D transform = new AffineTransform3D();
        transform.scale(cal.pixelWidth, cal.pixelHeight, cal.pixelDepth);

        // RandomAccessibleIntervalSource reports "px" unless told otherwise; the report labels its
        // numbers with that unit, so the calibration of the TIFF is carried over explicitly.
        VoxelDimensions voxelDimensions = new FinalVoxelDimensions(
                cal.getUnit(), cal.pixelWidth, cal.pixelHeight, cal.pixelDepth);
        Source<FloatType> source = new RandomAccessibleIntervalSource<FloatType>(
                rai, new FloatType(), transform, imp.getTitle()) {
            @Override
            public VoxelDimensions getVoxelDimensions() {
                return voxelDimensions;
            }
        };
        return SourceHelper.createSourceAndConverter(source);
    }
}
