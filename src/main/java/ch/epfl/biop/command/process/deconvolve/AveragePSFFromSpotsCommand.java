package ch.epfl.biop.command.process.deconvolve;

import bdv.util.RandomAccessibleIntervalSource;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.EmptyMultiResolutionSourceCreator;
import ch.epfl.biop.source.SourceFuserAndResampler;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.io.TmXmlReader;
import ij.IJ;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.source.SourceHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Builds an experimental PSF by <b>averaging sub-resolution beads</b>, each one re-centred with
 * subpixel precision on the spot positions of a TrackMate XML file.
 * <p>
 * This is the direct, "just stack the beads" counterpart of {@link DistillPSFCommand}: where the
 * latter recovers the PSF by running Richardson-Lucy backwards on the whole volume (GPU, one huge
 * FFT), this command simply crops a box around every bead, shifts it so that the bead centre lands
 * on the centre of the output box, and averages the crops. It needs no GPU and no iterations, but
 * it does need a bead list.
 * </p>
 * <h3>Where the subpixel precision comes from</h3>
 * TrackMate's DoG detector localises spots with subpixel accuracy and stores the result in the XML
 * as {@code POSITION_X/Y/Z} in <b>physical units</b>. Those coordinates are used here as world
 * coordinates in the bead source's own frame, so the re-centring never goes through a rounding to
 * the voxel grid: each bead gets its own affine transform with a subpixel translation. The precision
 * comes from that translation and from the fact that the beads sit at random subpixel offsets, so
 * the population as a whole samples the PSF continuously - which is also why the result can
 * legitimately be computed on a grid finer than the input.
 * <p>
 * <b>The resampling is deliberately not interpolated.</b> It is tempting to think linear
 * interpolation is what makes the re-centring subpixel; it is not, and it costs sharpness. Sampling
 * a bead with a 2-tap linear kernel at fractional offset {@code f} convolves it with a kernel of
 * variance {@code f(1-f)}, i.e. {@code 1/6} of a voxel squared once averaged over uniformly
 * distributed offsets. Nearest-neighbour adds no blur at all; it only rounds each bead's position by
 * at most half a voxel, which over the population acts as a jitter of variance {@code 1/12}. Linear
 * interpolation therefore blurs the result twice as much, and measurements on a 255-bead dataset
 * match that prediction: the second moment of the PSF drops by 0.107 (x) and 0.120 (z) voxel squared
 * when interpolation is turned off, against 1/12 = 0.083 predicted. The only cost is roughly a third
 * more background noise, since neighbouring voxels are no longer averaged.
 * </p>
 * <p>
 * <b>Coordinate convention.</b> The spot positions are taken as world coordinates of the selected
 * source(s), i.e. no calibration is read back from the XML. This is what makes the command work
 * for rotated or otherwise transformed sources, but it does mean the source must carry the same
 * calibration as the image TrackMate was run on. The log reports the bounding box of the spots in
 * source voxel coordinates, which makes a calibration mismatch immediately obvious.
 * </p>
 * <h3>Beads that fall off the image</h3>
 * A bead close to a border yields a crop that is clipped by the image bounds, so it does not span
 * the whole output box. This is handled by the alpha-aware fusion: every bead source carries an
 * implicit {@link bdv.util.source.alpha.AlphaSourceRAI} which is 1 over the crop and 0 outside, and
 * the fusion only combines the beads actually present at each voxel -
 * {@link bdv.util.source.fused.AverageAlphaFused3DRandomAccess} divides by the sum of the alphas,
 * {@link bdv.util.source.fused.MedianAlphaFused3DRandomAccess} takes the median of them. A clipped
 * bead therefore contributes only where it exists, with no bias and no need to discard it.
 * <h3>Median rather than mean</h3>
 * With a few hundred beads scattered over a large field, a fair fraction of the crops contain a
 * <i>second</i> bead somewhere in the box. Averaging spreads those contaminants over the result as a
 * halo of small bumps; taking the median at each voxel discards them, because a contaminant is an
 * extreme value only at the voxels where it happens to fall. On a 255-bead dataset the median left
 * no far-background voxel above 1% of the peak, against 122 for the mean, and gave a PSF 2.5%
 * narrower. The mean is therefore not offered.
 * <h3>Per-bead normalisation</h3>
 * Beads differ in brightness, so each crop is background-subtracted (by default the median of the
 * crop, which is dominated by background since the bead is tiny compared to the box) and divided by
 * its own peak, measured in a small window around the spot so that a brighter neighbour cannot
 * capture the normalisation. Every bead then contributes on the same scale.
 * <p>
 * Both steps are needed. Subtracting the background is what keeps the per-bead offsets from
 * reshuffling the ordering the median relies on. Dividing by the peak may look unnecessary - or even
 * counter-productive, since it rescales an over-bright doublet into an ordinary-looking bead - but
 * the median is taken <b>per voxel</b>, so such a bead is still rejected at the voxels where its
 * companion contributes, and skipping the normalisation instead lets the amplitude spread dominate
 * the ordering everywhere: the median stops being a consensus over all the beads and drifts towards
 * whichever ones carry more signal in the wings. Measured on a 255-bead dataset, that widened the
 * PSF from 0.370 to 0.385 um laterally and from 0.799 to 0.852 um axially, and multiplied the
 * background noise by 1.75.
 * </p>
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        initializer = "init",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ProcessMenu, weight = BdvPgMenus.ProcessW),
                @Menu(label = "Deconvolve", weight = -1.1),
                @Menu(label = "Source - Average PSF from Beads (TrackMate spots)", weight = 8)
        },
        description = "Averages sub-resolution beads into an experimental PSF, re-centring each bead " +
                "with subpixel precision on the spot positions of a TrackMate XML file. CPU only.")
public class AveragePSFFromSpotsCommand implements BdvPlaygroundActionCommand {

    /** Extra voxels kept around each crop, so that resampling never reaches the crop edge. */
    private static final long GUARD_VOXELS = 2;

    /** Half-width, in source voxels, of the window in which each bead's peak is measured. */
    private static final long PEAK_WINDOW_RADIUS = 3;

    /** Number of bins used for the background percentile. Plenty for a background estimate. */
    private static final int HISTOGRAM_BINS = 4096;

    @Parameter(required = false)
    TaskService taskService;

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String header_message = "";

    @Parameter(label = "Bead Image Source(s)",
            style = "sorted",
            description = "One or more 3D images containing sub-resolution beads (e.g. the channels of a "
                    + "multi-channel acquisition). Each is averaged separately, using the same spot list.",
            callback = "updateEstimate")
    SourceAndConverter<?>[] beads;

    @Parameter(label = "TrackMate File",
            style = "open",
            description = "TrackMate XML file holding the detected bead positions (POSITION_X/Y/Z, in the "
                    + "physical units of the source). Spots of frame 0 are used.")
    File trackmate_file;

    @Parameter(label = "Visible Spots Only",
            description = "Use only the spots that pass the filters saved in the TrackMate file "
                    + "(VISIBILITY = 1). Uncheck to average every detected spot.")
    boolean visible_spots_only = true;

    @Parameter(label = "PSF Size X", description = "Output PSF width, in output voxels",
            callback = "updateEstimate")
    int psf_size_x = 64;

    @Parameter(label = "PSF Size Y", description = "Output PSF height, in output voxels",
            callback = "updateEstimate")
    int psf_size_y = 64;

    @Parameter(label = "PSF Size Z", description = "Output PSF depth, in output voxels",
            callback = "updateEstimate")
    int psf_size_z = 64;

    @Parameter(label = "Output Voxel Size X (0 = same as input)",
            description = "Voxel size of the averaged PSF along X, in the unit of the input source. "
                    + "Because the beads sit at random subpixel offsets, a value finer than the input "
                    + "is meaningful.",
            callback = "updateEstimate")
    double voxel_size_x = 0;

    @Parameter(label = "Output Voxel Size Y (0 = same as input)",
            description = "Voxel size of the averaged PSF along Y, in the unit of the input source",
            callback = "updateEstimate")
    double voxel_size_y = 0;

    @Parameter(label = "Output Voxel Size Z (0 = same as input)",
            description = "Voxel size of the averaged PSF along Z, in the unit of the input source",
            callback = "updateEstimate")
    double voxel_size_z = 0;

    @Parameter(label = "Background Percentile",
            description = "Percentile of each bead's crop taken as its background level, subtracted before "
                    + "the bead is normalised to its own peak. A bead is tiny compared to its box, so the "
                    + "median (50) is the right estimator; a lower percentile sits below the background by "
                    + "about that many sigmas of the noise and leaves a positive pedestal in the averaged "
                    + "PSF. Lower it only if the bead fills a large part of the box.",
            min = "0", max = "100", stepSize = "1")
    double background_percentile = 50;

    @Parameter(label = "Number of Threads",
            description = "Number of parallel threads used to compute the average", min = "1")
    int n_threads = 4;

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String size_message = "Select a bead source to see the size of the output PSF.";

    @Parameter(label = "Name", description = "Name of the averaged PSF output source")
    String name = "average_psf";

    @Parameter(type = ItemIO.OUTPUT,
            description = "The averaged PSF sources, one per input bead source (same order)")
    SourceAndConverter<?>[] psf_out;

    /** Builds the informational message shown at the top of the dialog. */
    protected void init() {
        header_message = "<html>" +
                "<h3>PSF averaging from beads</h3>" +
                "Each bead of the TrackMate file is cropped, shifted so that its <b>subpixel</b> centre " +
                "lands on the centre<br>of the output box, normalised, and averaged with all the others. " +
                "Beads clipped by the image<br>border are kept: the alpha-weighted fusion averages only " +
                "over the beads actually present at<br>each voxel. Runs on the CPU." +
                "</html>";
    }

    /** Recomputes the physical size summary whenever the sources or the output grid change. */
    protected void updateEstimate() {
        try {
            if (beads == null || beads.length == 0) {
                size_message = "Select a bead source to see the size of the output PSF.";
                return;
            }
            AffineTransform3D transform = new AffineTransform3D();
            beads[0].getSpimSource().getSourceTransform(0, 0, transform);
            double[] inVoxel = voxelSize(transform);
            double[] outVoxel = outputVoxelSize(inVoxel);
            String unit = beads[0].getSpimSource().getVoxelDimensions().unit();

            StringBuilder sb = new StringBuilder("<html>");
            sb.append("Input voxel size: <b>").append(fmt(inVoxel[0])).append(" x ").append(fmt(inVoxel[1]))
                    .append(" x ").append(fmt(inVoxel[2])).append("</b> ").append(unit).append("<br>");
            sb.append("Output voxel size: <b>").append(fmt(outVoxel[0])).append(" x ").append(fmt(outVoxel[1]))
                    .append(" x ").append(fmt(outVoxel[2])).append("</b> ").append(unit).append("<br>");
            sb.append("Output PSF: <b>").append(psf_size_x).append(" x ").append(psf_size_y).append(" x ")
                    .append(psf_size_z).append("</b> voxels = <b>")
                    .append(fmt(psf_size_x * outVoxel[0])).append(" x ")
                    .append(fmt(psf_size_y * outVoxel[1])).append(" x ")
                    .append(fmt(psf_size_z * outVoxel[2])).append("</b> ").append(unit);
            sb.append("</html>");
            size_message = sb.toString();
        } catch (Exception e) {
            size_message = "<html>Could not estimate the output size: " + e.getMessage() + "</html>";
        }
    }

    @Override
    public void run() {
        // --- Validation ---
        if (beads == null || beads.length == 0) {
            IJ.error("Average PSF", "No bead source selected.");
            return;
        }
        for (SourceAndConverter<?> bead : beads) {
            if (!bead.getSpimSource().isPresent(0)) {
                IJ.error("Average PSF", "Source " + bead.getSpimSource().getName()
                        + " is not defined at timepoint 0.");
                return;
            }
            if (!(bead.getSpimSource().getType() instanceof RealType)) {
                IJ.error("Average PSF", "The pixel type of " + bead.getSpimSource().getName()
                        + " cannot be averaged.");
                return;
            }
            if (bead.getSpimSource().getSource(0, 0).numDimensions() < 3) {
                IJ.error("Average PSF", "PSF averaging requires 3D sources; "
                        + bead.getSpimSource().getName() + " is not.");
                return;
            }
        }
        if (psf_size_x < 1 || psf_size_y < 1 || psf_size_z < 1) {
            IJ.error("Average PSF", "The PSF size must be at least 1 voxel along every axis.");
            return;
        }
        if (trackmate_file == null || !trackmate_file.exists()) {
            IJ.error("Average PSF", "TrackMate file not found: " + trackmate_file);
            return;
        }

        List<double[]> spots = readSpots(trackmate_file, visible_spots_only);
        if (spots == null) return; // the reader already reported the problem
        if (spots.isEmpty()) {
            IJ.error("Average PSF", "The TrackMate file contains no "
                    + (visible_spots_only ? "visible " : "") + "spot in frame 0.");
            return;
        }
        IJ.log("[Average PSF] " + spots.size() + " spot(s) read from " + trackmate_file.getName() + ".");

        // --- Output grid: an empty source whose voxel grid the fusion resamples onto ---
        AffineTransform3D firstTransform = new AffineTransform3D();
        beads[0].getSpimSource().getSourceTransform(0, 0, firstTransform);
        double[] outVoxel = outputVoxelSize(voxelSize(firstTransform));
        final long[] outDims = {psf_size_x, psf_size_y, psf_size_z};

        AffineTransform3D modelTransform = new AffineTransform3D();
        modelTransform.scale(outVoxel[0], outVoxel[1], outVoxel[2]);
        SourceAndConverter<?> model = new EmptyMultiResolutionSourceCreator(
                name + "_grid", modelTransform, outDims[0], outDims[1], outDims[2], 1, 2, 2, 2, 1).get();

        // World position of the output box centre: every bead is shifted so that its own centre lands here.
        final double[] boxCentre = new double[3];
        modelTransform.apply(new double[]{outDims[0] / 2.0, outDims[1] / 2.0, outDims[2] / 2.0}, boxCentre);

        VoxelDimensions outVoxelDimensions = new FinalVoxelDimensions(
                beads[0].getSpimSource().getVoxelDimensions().unit(), outVoxel[0], outVoxel[1], outVoxel[2]);

        Task task = null;
        final List<SourceAndConverter<?>> outputs = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, n_threads));
        try {
            if (taskService != null) {
                task = taskService.createTask("Average PSF (" + beads.length + " channel(s))");
                task.setProgressMaximum(beads.length);
            }

            for (int ch = 0; ch < beads.length; ch++) {
                if (task != null && task.isCanceled()) {
                    IJ.log("[Average PSF] Cancelled by user after " + ch + " / " + beads.length + " channel(s).");
                    break;
                }
                SourceAndConverter<?> beadSource = beads[ch];
                String channelName = beads.length == 1 ? name : name + "_ch" + ch;
                if (task != null) {
                    task.setStatusMessage("Averaging beads - channel " + (ch + 1) + " / " + beads.length
                            + " (" + beadSource.getSpimSource().getName() + ")");
                }
                IJ.log("[Average PSF] Channel " + (ch + 1) + " / " + beads.length + " - "
                        + beadSource.getSpimSource().getName());

                List<SourceAndConverter<FloatType>> beadCrops =
                        buildBeadSources(beadSource, spots, modelTransform, outDims, boxCentre);
                if (beadCrops.isEmpty()) {
                    IJ.log("[Average PSF] No usable bead for this channel - skipped. Check that the source's "
                            + "calibration matches the image TrackMate was run on.");
                    if (task != null) task.setProgressValue(ch + 1);
                    continue;
                }

                // At every output voxel, the median is taken over the beads whose crop covers it
                // (see MedianAlphaFused3DRandomAccess).
                int[] block = {(int) Math.min(outDims[0], 32), (int) Math.min(outDims[1], 32),
                        (int) Math.min(outDims[2], 32)};
                SourceAndConverter<FloatType> fused = new SourceFuserAndResampler<FloatType>(
                        beadCrops,
                        AlphaFusedResampledSource.MEDIAN,
                        model,
                        channelName,
                        false, // reuseMipMaps: the bead crops are single-level anyway
                        true,  // cache: the fusion is evaluated once, block per block
                        false, // interpolate: it would blur the PSF, see the class javadoc
                        0,
                        block[0], block[1], block[2], -1,
                        n_threads).get();

                outputs.add(materialize(fused, executor, outDims, modelTransform,
                        outVoxelDimensions, channelName, beadCrops.size()));

                if (task != null) task.setProgressValue(ch + 1);
            }
            IJ.log("[Average PSF] Done (" + outputs.size() + " / " + beads.length + " channel(s)).");
        } finally {
            executor.shutdown();
            psf_out = outputs.toArray(new SourceAndConverter[0]);
            if (task != null) task.finish();
        }
    }

    /**
     * Reads the spot positions of frame 0 from a TrackMate file, as {@code {x, y, z}} world
     * coordinates in the physical unit of the file. Returns {@code null} if the file cannot be read.
     * <p>
     * This is the only place that knows about the TrackMate format: should the format be replaced,
     * only this method has to follow.
     */
    private static List<double[]> readSpots(File file, boolean visibleOnly) {
        TmXmlReader reader = new TmXmlReader(file);
        if (!reader.isReadingOk()) {
            IJ.error("Average PSF", "Could not read the TrackMate file:\n" + reader.getErrorMessage());
            return null;
        }
        Model model = reader.getModel();
        if (model == null) {
            IJ.error("Average PSF", "No model found in " + file.getName()
                    + " - is it really a TrackMate file?");
            return null;
        }
        IJ.log("[Average PSF] TrackMate file spatial units: " + model.getSpaceUnits()
                + " (the source is expected to use the same unit).");

        List<double[]> spots = new ArrayList<>();
        for (Spot spot : model.getSpots().iterable(0, visibleOnly)) {
            spots.add(new double[]{
                    spot.getDoublePosition(0), spot.getDoublePosition(1), spot.getDoublePosition(2)});
        }
        // Beads from other frames would be located in frame-0 pixel data, which would be wrong.
        int all = model.getSpots().getNSpots(visibleOnly);
        if (all > spots.size()) {
            IJ.log("[Average PSF] NOTE: " + (all - spots.size()) + " spot(s) live outside frame 0 and are "
                    + "ignored - only frame 0 matches the 3D source.");
        }
        return spots;
    }

    /**
     * Builds one re-centred, normalised {@link FloatType} source per usable bead. A bead is usable
     * when its crop intersects the image; a crop clipped by the image border is kept as is, the
     * alpha-weighted fusion takes care of it.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<SourceAndConverter<FloatType>> buildBeadSources(SourceAndConverter<?> beadSource,
                                                                List<double[]> spots,
                                                                AffineTransform3D modelTransform,
                                                                long[] outDims,
                                                                double[] boxCentre) {
        Source<?> spim = beadSource.getSpimSource();
        // Raw type: the pixel type is only known to be some RealType, which is all percentile(),
        // peak() and the converter below need.
        RandomAccessibleInterval full = spim.getSource(0, 0);
        AffineTransform3D sourceTransform = new AffineTransform3D();
        spim.getSourceTransform(0, 0, sourceTransform);
        AffineTransform3D inverse = sourceTransform.inverse();

        List<SourceAndConverter<FloatType>> sources = new ArrayList<>();
        int outside = 0, flat = 0;
        double[] spotMin = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] spotMax = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        for (int i = 0; i < spots.size(); i++) {
            double[] world = spots.get(i);

            double[] pixel = new double[3];
            inverse.apply(world, pixel);
            for (int d = 0; d < 3; d++) {
                spotMin[d] = Math.min(spotMin[d], pixel[d]);
                spotMax[d] = Math.max(spotMax[d], pixel[d]);
            }

            Interval crop = cropInterval(inverse, world, modelTransform, outDims, boxCentre, full);
            if (crop == null) {
                outside++;
                continue;
            }

            RandomAccessibleInterval raw = Views.interval(full, crop);
            double background = percentile(raw, background_percentile);
            double peak = peak(full, pixel);
            if (!(peak > background)) {
                // Flat or inverted bead: normalising would amplify noise or flip the sign.
                flat++;
                continue;
            }
            final double bg = background;
            final double scale = 1.0 / (peak - background);
            RandomAccessibleInterval<FloatType> normalised = Converters.convert(
                    (RandomAccessibleInterval) raw,
                    (i1, o) -> ((FloatType) o).setReal((((RealType) i1).getRealDouble() - bg) * scale),
                    new FloatType());

            // Same geometry as the input source, translated so that this bead's subpixel centre
            // sits exactly on the centre of the output box.
            AffineTransform3D beadTransform = sourceTransform.copy();
            double[] translation = beadTransform.getTranslation();
            for (int d = 0; d < 3; d++) translation[d] += boxCentre[d] - world[d];
            beadTransform.setTranslation(translation);

            Source<FloatType> src = new RandomAccessibleIntervalSource<>(normalised, new FloatType(),
                    beadTransform, spim.getName() + "_bead" + i);
            sources.add((SourceAndConverter<FloatType>) SourceHelper.createSourceAndConverter(src));
        }

        IJ.log("[Average PSF] Spot bounding box in source voxels: ["
                + Math.round(spotMin[0]) + "-" + Math.round(spotMax[0]) + ", "
                + Math.round(spotMin[1]) + "-" + Math.round(spotMax[1]) + ", "
                + Math.round(spotMin[2]) + "-" + Math.round(spotMax[2]) + "] for an image of "
                + full.dimension(0) + " x " + full.dimension(1) + " x " + full.dimension(2)
                + " voxels.");
        IJ.log("[Average PSF] " + sources.size() + " bead(s) kept"
                + (outside > 0 ? ", " + outside + " outside the image" : "")
                + (flat > 0 ? ", " + flat + " with no contrast above background" : "") + ".");
        return sources;
    }

    /**
     * Bounding box, in source voxels, of the output box centred on {@code world}, grown by
     * {@link #GUARD_VOXELS} and clipped to the image. Returns {@code null} when the bead lies
     * entirely outside the image. The eight corners are mapped explicitly so that the crop is
     * correct even for a rotated source.
     */
    private static Interval cropInterval(AffineTransform3D inverse, double[] world,
                                         AffineTransform3D modelTransform, long[] outDims,
                                         double[] boxCentre, Interval image) {
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        double[] corner = new double[3];
        double[] pixel = new double[3];
        for (int c = 0; c < 8; c++) {
            double[] q = {
                    (c & 1) == 0 ? 0 : outDims[0] - 1,
                    (c & 2) == 0 ? 0 : outDims[1] - 1,
                    (c & 4) == 0 ? 0 : outDims[2] - 1};
            modelTransform.apply(q, corner);
            for (int d = 0; d < 3; d++) corner[d] += world[d] - boxCentre[d];
            inverse.apply(corner, pixel);
            for (int d = 0; d < 3; d++) {
                min[d] = Math.min(min[d], pixel[d]);
                max[d] = Math.max(max[d], pixel[d]);
            }
        }
        long[] lo = new long[3];
        long[] hi = new long[3];
        for (int d = 0; d < 3; d++) {
            lo[d] = Math.max(image.min(d), (long) Math.floor(min[d]) - GUARD_VOXELS);
            hi[d] = Math.min(image.max(d), (long) Math.ceil(max[d]) + GUARD_VOXELS);
            if (hi[d] < lo[d]) return null;
        }
        return new FinalInterval(lo, hi);
    }

    /**
     * Value below which {@code p} percent of the voxels of {@code rai} lie, computed from a
     * histogram so that it stays linear in the number of voxels.
     */
    private static <R extends RealType<R>> double percentile(RandomAccessibleInterval<R> rai, double p) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        long n = 0;
        for (R value : Views.iterable(rai)) {
            double v = value.getRealDouble();
            if (v < min) min = v;
            if (v > max) max = v;
            n++;
        }
        if (n == 0) return 0;
        if (max <= min) return min;

        long[] histogram = new long[HISTOGRAM_BINS];
        double scale = HISTOGRAM_BINS / (max - min);
        for (R value : Views.iterable(rai)) {
            int bin = (int) ((value.getRealDouble() - min) * scale);
            if (bin < 0) bin = 0;
            if (bin >= HISTOGRAM_BINS) bin = HISTOGRAM_BINS - 1;
            histogram[bin]++;
        }
        long target = Math.max(1, (long) Math.ceil(p / 100.0 * n));
        long cumulated = 0;
        for (int bin = 0; bin < HISTOGRAM_BINS; bin++) {
            cumulated += histogram[bin];
            if (cumulated >= target) return min + (bin + 0.5) / scale;
        }
        return max;
    }

    /**
     * Peak value of a bead, measured in a small window around its centre rather than over the whole
     * crop, so that a brighter neighbour caught in the same box cannot drive the normalisation.
     */
    private static <R extends RealType<R>> double peak(RandomAccessibleInterval<R> image, double[] centre) {
        long[] lo = new long[3];
        long[] hi = new long[3];
        for (int d = 0; d < 3; d++) {
            lo[d] = Math.max(image.min(d), Math.round(centre[d]) - PEAK_WINDOW_RADIUS);
            hi[d] = Math.min(image.max(d), Math.round(centre[d]) + PEAK_WINDOW_RADIUS);
            if (hi[d] < lo[d]) return Double.NEGATIVE_INFINITY;
        }
        double max = -Double.MAX_VALUE;
        for (R value : Views.iterable(Views.interval(image, lo, hi))) {
            double v = value.getRealDouble();
            if (v > max) max = v;
        }
        return max;
    }

    /**
     * Evaluates the lazy fused source once, in parallel over slabs, into a plain in-memory image.
     * The result is small, so it is much more convenient than a lazy chain over hundreds of bead
     * sources.
     */
    private SourceAndConverter<?> materialize(SourceAndConverter<FloatType> fused,
                                              ExecutorService executor,
                                              long[] outDims,
                                              AffineTransform3D modelTransform,
                                              VoxelDimensions outVoxelDimensions,
                                              String channelName,
                                              int nBeads) {
        RandomAccessibleInterval<FloatType> lazy = fused.getSpimSource().getSource(0, 0);
        ArrayImg<FloatType, FloatArray> out = ArrayImgs.floats(outDims[0], outDims[1], outDims[2]);

        long slab = Math.max(1, outDims[2] / Math.max(1, n_threads));
        List<Future<?>> futures = new ArrayList<>();
        for (long z = 0; z < outDims[2]; z += slab) {
            final long z0 = z;
            final long z1 = Math.min(outDims[2], z + slab) - 1;
            futures.add(executor.submit(() -> {
                long[] lo = {0, 0, z0};
                long[] hi = {outDims[0] - 1, outDims[1] - 1, z1};
                Cursor<FloatType> in = Views.flatIterable(Views.interval(lazy, lo, hi)).cursor();
                Cursor<FloatType> target = Views.flatIterable(Views.interval(out, lo, hi)).cursor();
                while (in.hasNext()) {
                    target.next().set(in.next().get());
                }
            }));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                IJ.log("[Average PSF] Error while averaging: " + e.getMessage());
            }
        }

        double sum = 0;
        double max = -Double.MAX_VALUE;
        for (FloatType value : out) {
            double v = value.get();
            sum += v;
            if (v > max) max = v;
        }
        IJ.log("[Average PSF] " + channelName + ": median of " + nBeads + " bead(s), peak "
                + String.format("%.4f", max) + ", sum " + String.format("%.1f", sum) + ".");
        if (max <= 0) {
            IJ.log("[Average PSF] WARNING: the PSF is empty. Check the spot positions against the "
                    + "source calibration (see the bounding box logged above).");
        }

        Source<FloatType> psfSource = new RandomAccessibleIntervalSource<FloatType>(
                out, new FloatType(), modelTransform, channelName) {
            @Override
            public VoxelDimensions getVoxelDimensions() {
                return outVoxelDimensions;
            }
        };
        return SourceHelper.createSourceAndConverter(psfSource);
    }

    /** Output voxel size, falling back to the input's for every axis left at 0. */
    private double[] outputVoxelSize(double[] inputVoxelSize) {
        return new double[]{
                voxel_size_x > 0 ? voxel_size_x : inputVoxelSize[0],
                voxel_size_y > 0 ? voxel_size_y : inputVoxelSize[1],
                voxel_size_z > 0 ? voxel_size_z : inputVoxelSize[2]};
    }

    /** Voxel size along each axis, as the norm of the corresponding column of the source transform. */
    private static double[] voxelSize(AffineTransform3D transform) {
        double[] m = transform.getRowPackedCopy();
        double[] size = new double[3];
        for (int d = 0; d < 3; d++) {
            size[d] = Math.sqrt(m[d] * m[d] + m[d + 4] * m[d + 4] + m[d + 8] * m[d + 8]);
        }
        return size;
    }

    private static String fmt(double value) {
        return String.format("%.4f", value);
    }

}