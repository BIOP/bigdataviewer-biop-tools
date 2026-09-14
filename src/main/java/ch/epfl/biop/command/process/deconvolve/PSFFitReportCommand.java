/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ch.epfl.biop.command.process.deconvolve;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ij.IJ;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.fitting.leastsquares.MultivariateJacobianFunction;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.Pair;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <b>ALPHA - work in progress.</b> Measures where the peak of a PSF sits and how wide it is, one
 * channel at a time, by fitting a 1D Gaussian along each of the three voxel axes.
 * <p>
 * The intended use is to check the PSFs distilled by {@link AveragePSFFromSpotsCommand} or
 * {@link DistillPSFCommand}: a well-behaved multi-channel PSF stack should have every channel
 * centred on the centre of its box, and the residual channel-to-channel displacement is the
 * chromatic shift of the objective, which is exactly what one wants to know before deconvolving and
 * before trusting a colocalisation measurement. The FWHM along each axis is the companion number:
 * it says whether the distillation produced something of the expected size or a blurred mess.
 * </p>
 * <p>
 * The result is a small Markdown table, written to the log and returned as an output, with one row
 * per channel. The acquisition metadata above it - microscope, objective, NA, immersion, date - is
 * typed in by the user, because none of it survives into a distilled PSF, and a report that does not
 * say which objective it describes is of no use once it has been pasted somewhere.
 * </p>
 * <h2>What is fitted, and where</h2>
 * The peak is first located as the argument of the maximum of a 3x3x3 box mean, so that a single hot
 * voxel cannot capture it. Three line profiles are then taken through that voxel, one per axis, and
 * each is fitted with
 * <pre>  f(x) = A exp(-(x - mu)^2 / (2 sigma^2)) + B</pre>
 * by Levenberg-Marquardt. The constant {@code B} matters: without it the fit trades background
 * against width and the FWHM comes out too large. The fit is restricted to a window of
 * {@code fit_window} times the half-maximum width around the peak, because a real PSF is not
 * Gaussian in the wings - laterally it has Airy rings, axially it has side lobes - and including
 * them would drag {@code sigma} up. The window is set from a first, non-parametric half-maximum
 * width, then the fit is run a second time on a window recentred on the fitted position, so that the
 * result does not depend on where the initial integer maximum happened to land.
 * <p>
 * The coefficient of determination R&sup2; is reported per axis over the fit window. It is a
 * <i>fit</i> quality, not a PSF quality: a value well below 0.99 usually means the profile is not
 * peaked in the way the model assumes - a double bead, a clipped peak, a strong background gradient -
 * and the corresponding position and FWHM should not be trusted.
 * </p>
 * <h2>Coordinate conventions</h2>
 * The fit lives on the voxel grid, so the fitted position {@code mu} is a continuous pixel
 * coordinate. It is reported against the centre of the box, taken at pixel coordinate
 * {@code dimension / 2}, which is the convention {@link AveragePSFFromSpotsCommand} uses to place
 * the beads: a perfectly centred PSF of 128 voxels fits {@code mu = 64.0}. The displacement is then
 * turned into physical units through the linear part of the source transform, so it is calibrated
 * and it is correct even if the source carries a stage translation.
 * <p>
 * The absolute displacement against the box centre depends on that convention; the displacement
 * <i>between</i> channels does not, and is the more meaningful of the two. It is reported against
 * the channel given by {@code reference_channel}.
 * </p>
 * <p>
 * <b>Caveat on rotated sources.</b> The three profiles follow the voxel axes, not the world axes. If
 * the source transform contains a rotation, "FWHM along x" means along the first voxel axis, and the
 * physical scale used for it is the norm of the corresponding column of the transform. For the
 * axis-aligned, scale-only transforms that PSF sources normally carry, the two coincide.
 * </p>
 * <p>
 * <b>Still to do</b>: a graphical output of the profiles and their fits, a full 3D fit (which would
 * see a tilted PSF, where three axis profiles cannot), an anisotropy / asymmetry figure of merit,
 * and confidence intervals on the fitted parameters.
 * </p>
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        initializer = "init",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ProcessMenu, weight = BdvPgMenus.ProcessW),
                @Menu(label = "Deconvolve", weight = -1.1),
                @Menu(label = "Source - PSF Centre and FWHM Report (ALPHA)", weight = 9)
        },
        description = "ALPHA - fits a 1D Gaussian along x, y and z on each PSF source and reports, as "
                + "a small Markdown table, the sub-voxel displacement from the centre of the box and "
                + "the FWHM, in calibrated units. CPU only, and fast.")
public class PSFFitReportCommand implements BdvPlaygroundActionCommand {

    /** FWHM of a Gaussian, in units of its standard deviation. */
    private static final double FWHM_PER_SIGMA = 2.0 * Math.sqrt(2.0 * Math.log(2.0));

    /** Shortest profile that can carry a four-parameter fit with anything to spare. */
    private static final int MIN_PROFILE_LENGTH = 7;

    /** Radius, in voxels, of the box mean used to locate the peak robustly. */
    private static final int PEAK_SMOOTHING_RADIUS = 1;

    private static final String[] AXIS = {"x", "y", "z"};

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String header_message = "";

    @Parameter(label = "PSF Source(s)",
            style = "sorted",
            description = "One 3D PSF per channel, in the order the channels should be reported. "
                    + "Each is fitted independently.")
    SourceAndConverter<?>[] psf_sources;

    @Parameter(label = "Reference Channel (-1 = none)",
            description = "Index in the list above of the channel the others are compared to, for the "
                    + "channel-to-channel displacement. Set to -1 to report only the displacement "
                    + "against the centre of the box.",
            min = "-1")
    int reference_channel = 0;

    @Parameter(label = "Fit Window (x FWHM)",
            description = "Half-width of the fitted profile window, as a multiple of the measured "
                    + "half-maximum width. A PSF is Gaussian only near its peak, so a small window "
                    + "gives a truer FWHM; too small a window and the fit has too few points. 1.5 is a "
                    + "reasonable compromise.",
            min = "0.5", max = "10", stepSize = "0.1")
    double fit_window = 1.5;

    @Parameter(label = "Resolution Level",
            description = "Mipmap level of the sources to measure. 0 is full resolution and is almost "
                    + "always what is wanted.",
            min = "0")
    int level = 0;

    @Parameter(label = "Timepoint", min = "0")
    int timepoint = 0;

    // --- Acquisition metadata. None of it is readable from a distilled PSF, so it is typed in and
    // copied verbatim into the report header, which is the whole point: the report is meant to be
    // pasted somewhere and still say which objective it describes. ---

    @Parameter(label = "Microscope", required = false)
    String microscope = "";

    @Parameter(label = "Objective", required = false)
    String objective = "";

    @Parameter(label = "NA (0 = unknown)", required = false, min = "0")
    double numerical_aperture = 0;

    @Parameter(label = "Immersion", required = false,
            choices = {"air", "water", "oil", "glycerol", "silicone"})
    String immersion = "";

    @Parameter(label = "Date", persist = false, required = false,
            description = "Acquisition date, free text. Defaults to today.")
    String date = "";

    @Parameter(label = "Channel Names (comma separated)", required = false,
            description = "One name per source, in the same order, e.g. \"405, 488, 561, 640\". "
                    + "Left empty - or with the wrong count - the source names are used instead.")
    String channel_names = "";

    @Parameter(type = ItemIO.OUTPUT,
            description = "The report, as Markdown - the same text that is written to the log")
    String psf_report;

    protected void init() {
        header_message = "<html>" +
                "<h3>PSF centre and FWHM &mdash; <font color=#b00000>ALPHA</font></h3>" +
                "Fits <b>A&middot;exp(-(x-&mu;)&sup2;/2&sigma;&sup2;) + B</b> along each voxel axis, through the " +
                "peak of every source.<br>Reports the sub-voxel displacement from the centre of the box " +
                "and the FWHM, in calibrated<br>units, as a small Markdown table. " +
                "Work in progress: no graphical output yet." +
                "</html>";
        date = LocalDate.now().toString();
    }

    @Override
    public void run() {
        if (psf_sources == null || psf_sources.length == 0) {
            IJ.error("PSF Fit Report", "No PSF source selected.");
            return;
        }
        if (reference_channel >= psf_sources.length) {
            IJ.error("PSF Fit Report", "The reference channel (" + reference_channel + ") is not in the "
                    + "list of " + psf_sources.length + " selected source(s).");
            return;
        }

        String[] labels = channelLabels();
        List<String> notes = new ArrayList<>();
        FitResult[] results = new FitResult[psf_sources.length];
        for (int ch = 0; ch < psf_sources.length; ch++) {
            try {
                results[ch] = measure(psf_sources[ch]);
                results[ch].label = labels[ch];
                for (String warning : results[ch].warnings) {
                    notes.add("`" + labels[ch] + "`: " + warning);
                }
            } catch (Exception e) {
                results[ch] = null;
                notes.add("`" + labels[ch] + "`: **not measured** - " + e.getMessage());
            }
        }

        psf_report = report(results, reference_channel >= 0 ? results[reference_channel] : null, notes);
        IJ.log(psf_report);
    }

    /**
     * One label per source: the comma-separated names if the user gave exactly as many as there are
     * sources, the source names otherwise. Getting the count wrong would silently mislabel every
     * channel, so a partial list is refused rather than padded.
     */
    private String[] channelLabels() {
        String[] labels = new String[psf_sources.length];
        for (int ch = 0; ch < labels.length; ch++) {
            labels[ch] = psf_sources[ch].getSpimSource().getName();
        }
        if (channel_names == null || channel_names.trim().isEmpty()) return labels;
        String[] given = channel_names.split(",");
        if (given.length != psf_sources.length) {
            IJ.log("[PSF Fit] " + given.length + " channel name(s) given for " + psf_sources.length
                    + " source(s) - the source names are used instead.");
            return labels;
        }
        for (int ch = 0; ch < labels.length; ch++) labels[ch] = given[ch].trim();
        return labels;
    }

    /** The whole report, as Markdown: a metadata header, one row per channel, then any notes. */
    private String report(FitResult[] results, FitResult reference, List<String> notes) {
        FitResult first = null;
        for (FitResult result : results) if (result != null) { first = result; break; }
        if (first == null) {
            return "## PSF report\n\nNo source could be measured.\n\n- " + String.join("\n- ", notes);
        }
        String unit = first.unit;

        StringBuilder sb = new StringBuilder("## PSF report");
        if (!microscope.trim().isEmpty()) sb.append(" - ").append(microscope.trim());
        sb.append("\n\n| | |\n|---|---|\n");
        row(sb, "Microscope", microscope.trim());
        row(sb, "Objective", objective.trim());
        row(sb, "NA", numerical_aperture > 0 ? String.format("%.2f", numerical_aperture) : "");
        row(sb, "Immersion", immersion == null ? "" : immersion.trim());
        row(sb, "Date", date == null ? "" : date.trim());
        row(sb, "Voxel size", triplet(first.voxelSize, "%.4f", " x ") + " " + unit);
        row(sb, "Fit window", String.format("%.1f", fit_window) + " x FWHM");

        boolean relative = reference != null;
        sb.append("\n| Channel | Shift vs box centre, x y z (").append(unit).append(") |");
        if (relative) sb.append(" Shift vs ").append(reference.label).append(" (").append(unit).append(") |");
        sb.append(" FWHM, x y z (").append(unit).append(") | R2, x y z |\n");
        sb.append("|---|---|").append(relative ? "---|" : "").append("---|---|\n");

        for (FitResult result : results) {
            if (result == null) continue;
            sb.append("| ").append(result.label);
            sb.append(" | ").append(triplet(result.shift, "%+.4f", ", "));
            if (relative) {
                if (result == reference) {
                    sb.append(" | -");
                } else {
                    double[] delta = new double[3];
                    for (int d = 0; d < 3; d++) delta[d] = result.shift[d] - reference.shift[d];
                    sb.append(" | ").append(triplet(delta, "%+.4f", ", "));
                }
            }
            sb.append(" | ").append(triplet(result.fwhm, "%.4f", ", "));
            sb.append(" | ").append(triplet(result.r2, "%.4f", ", "));
            sb.append(" |\n");
            // The voxel grid is what the shifts and widths are measured on, so a channel sampled
            // differently from the first one cannot be silently folded into the header row.
            if (!Arrays.equals(result.voxelSize, first.voxelSize)) {
                notes.add("`" + result.label + "`: voxel size "
                        + triplet(result.voxelSize, "%.4f", " x ") + " " + unit
                        + ", not the one in the header.");
            }
        }

        if (!notes.isEmpty()) {
            sb.append("\n").append("- ").append(String.join("\n- ", notes)).append("\n");
        }
        return sb.toString();
    }

    /** A header row, skipped when the user left the field empty. */
    private static void row(StringBuilder sb, String key, String value) {
        if (value != null && !value.isEmpty()) sb.append("| ").append(key).append(" | ")
                .append(value).append(" |\n");
    }

    /** Everything measured on one channel, in one place, so that a plot can be added later. */
    private static class FitResult {
        /** How the channel is named in the report: the user's name, or the source's. */
        String label;
        String unit;
        /** Voxel size along each axis, in {@link #unit}. */
        double[] voxelSize;
        /** Fitted peak position, as a continuous pixel coordinate, per axis. Not reported: the box
         *  centre it is measured against is the meaningful quantity. Kept for a future plot. */
        final double[] centrePixel = new double[3];
        /** Fitted peak position relative to the centre of the box, in calibrated units, per axis. */
        final double[] shift = new double[3];
        /** Gaussian FWHM in calibrated units, per axis. */
        final double[] fwhm = new double[3];
        /** Coefficient of determination of the fit over its window, per axis. */
        final double[] r2 = new double[3];
        /** The profiles that were fitted, and their fits - kept for a future graphical output. */
        final double[][] profile = new double[3][];
        final double[][] fitParameters = new double[3][];
        /** Anything the user should know about, e.g. a peak sitting on the border. */
        final List<String> warnings = new ArrayList<>();
    }

    /** Locates the peak of one source and fits a Gaussian along each voxel axis through it. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private FitResult measure(SourceAndConverter<?> sac) {
        Source<?> source = sac.getSpimSource();
        if (!(source.getType() instanceof RealType)) {
            throw new IllegalArgumentException("the pixel type of " + source.getName() + " is not numeric");
        }
        if (!source.isPresent(timepoint)) {
            throw new IllegalArgumentException(source.getName() + " is not defined at timepoint " + timepoint);
        }
        if (level >= source.getNumMipmapLevels()) {
            throw new IllegalArgumentException(source.getName() + " has only "
                    + source.getNumMipmapLevels() + " resolution level(s)");
        }
        RandomAccessibleInterval rai = source.getSource(timepoint, level);
        if (rai.numDimensions() < 3) {
            throw new IllegalArgumentException(source.getName() + " is not 3D");
        }

        FitResult result = new FitResult();
        result.label = source.getName();
        result.unit = source.getVoxelDimensions() == null ? "px" : source.getVoxelDimensions().unit();

        AffineTransform3D transform = new AffineTransform3D();
        source.getSourceTransform(timepoint, level, transform);
        double[] voxelSize = voxelSize(transform);
        result.voxelSize = voxelSize;

        long[] peak = peakPosition(rai);
        for (int d = 0; d < 3; d++) {
            if (peak[d] == rai.min(d) || peak[d] == rai.max(d)) {
                result.warnings.add("peak on the " + AXIS[d] + " border");
            }
        }

        for (int d = 0; d < 3; d++) {
            double[] profile = lineProfile(rai, peak, d);
            result.profile[d] = profile;
            if (profile.length < MIN_PROFILE_LENGTH) {
                throw new IllegalArgumentException("only " + profile.length + " voxel(s) along "
                        + AXIS[d] + ", too few to fit");
            }
            double[] fit = fitProfile(profile, (int) (peak[d] - rai.min(d)), result, d);
            result.fitParameters[d] = fit;
            // The profile is indexed from rai.min(d), which is 0 for the usual zero-min PSF image but
            // need not be.
            result.centrePixel[d] = rai.min(d) + fit[1];
            result.fwhm[d] = FWHM_PER_SIGMA * Math.abs(fit[2]) * voxelSize[d];
        }

        // Displacement against the centre of the box, mapped to world through the linear part of the
        // transform only: the translation is irrelevant here and would otherwise add the stage position.
        double[] deltaPixel = new double[3];
        for (int d = 0; d < 3; d++) {
            deltaPixel[d] = result.centrePixel[d] - (rai.min(d) + rai.dimension(d) / 2.0);
        }
        AffineTransform3D linear = transform.copy();
        linear.setTranslation(0, 0, 0);
        linear.apply(deltaPixel, result.shift);
        return result;
    }

    /**
     * Position of the maximum of a 3x3x3 box mean of {@code rai}. Smoothing before the search is what
     * keeps a single hot voxel - or a cosmic ray in an unaveraged bead image - from being taken for
     * the peak; it only ever moves the seed by a voxel or so, and the fit refines it anyway.
     */
    private static <R extends RealType<R>> long[] peakPosition(RandomAccessibleInterval<R> rai) {
        // Out-of-bounds extension rather than a shrunken search box: a PSF whose peak sits one voxel
        // from the border still gets a defined mean, and the border case is reported separately.
        RandomAccess<R> access = Views.extendBorder(rai).randomAccess();
        long[] position = defaultPosition(rai);
        long[] best = new long[3];
        double bestValue = -Double.MAX_VALUE;
        for (long z = rai.min(2); z <= rai.max(2); z++) {
            for (long y = rai.min(1); y <= rai.max(1); y++) {
                for (long x = rai.min(0); x <= rai.max(0); x++) {
                    double sum = 0;
                    for (long dz = -PEAK_SMOOTHING_RADIUS; dz <= PEAK_SMOOTHING_RADIUS; dz++) {
                        for (long dy = -PEAK_SMOOTHING_RADIUS; dy <= PEAK_SMOOTHING_RADIUS; dy++) {
                            for (long dx = -PEAK_SMOOTHING_RADIUS; dx <= PEAK_SMOOTHING_RADIUS; dx++) {
                                position[0] = x + dx;
                                position[1] = y + dy;
                                position[2] = z + dz;
                                access.setPosition(position);
                                sum += access.get().getRealDouble();
                            }
                        }
                    }
                    if (sum > bestValue) {
                        bestValue = sum;
                        best[0] = x;
                        best[1] = y;
                        best[2] = z;
                    }
                }
            }
        }
        return best;
    }

    /**
     * A position array holding the first index of every dimension. Only the first three are ever
     * moved; a source with more dimensions than three is measured on its first hyperslice.
     */
    private static long[] defaultPosition(RandomAccessibleInterval<?> rai) {
        long[] position = new long[rai.numDimensions()];
        for (int d = 0; d < position.length; d++) position[d] = rai.min(d);
        return position;
    }

    /** The whole line of {@code rai} along axis {@code d} that passes through {@code through}. */
    private static <R extends RealType<R>> double[] lineProfile(RandomAccessibleInterval<R> rai,
                                                                long[] through, int d) {
        RandomAccess<R> access = rai.randomAccess();
        long[] position = defaultPosition(rai);
        for (int i = 0; i < 3; i++) position[i] = through[i];
        double[] profile = new double[(int) rai.dimension(d)];
        for (int i = 0; i < profile.length; i++) {
            position[d] = rai.min(d) + i;
            access.setPosition(position);
            profile[i] = access.get().getRealDouble();
        }
        return profile;
    }

    /**
     * Fits {@code A exp(-(x-mu)^2 / 2 sigma^2) + B} to a profile and returns
     * {@code {A, mu, sigma, B}}, {@code mu} being an index into {@code profile}.
     * <p>
     * The initial guess comes from the half-maximum crossings, which need no model and cost nothing,
     * and which also set the width of the fitted window. The fit is then repeated once on a window
     * recentred on its own result, so that the answer does not depend on where the integer maximum
     * fell. {@code result.r2[d]} is filled in on the way.
     */
    private double[] fitProfile(double[] profile, int peakIndex, FitResult result, int d) {
        double background = median(profile);
        double amplitude = profile[peakIndex] - background;
        if (!(amplitude > 0)) {
            throw new IllegalArgumentException("the " + AXIS[d] + " profile has no contrast above its "
                    + "own median - is this really a PSF?");
        }
        double halfWidth = halfMaximumWidth(profile, peakIndex, background, amplitude);
        double[] guess = {amplitude, peakIndex, halfWidth / FWHM_PER_SIGMA, background};

        double[] fit = guess;
        double centre = peakIndex;
        double width = halfWidth;
        for (int pass = 0; pass < 2; pass++) {
            int from = (int) Math.max(0, Math.floor(centre - fit_window * width));
            int to = (int) Math.min(profile.length - 1, Math.ceil(centre + fit_window * width));
            if (to - from + 1 < 5) {
                // Too narrow a peak for a windowed fit: fall back to the whole profile rather than
                // fitting four parameters on four points.
                from = 0;
                to = profile.length - 1;
            }
            int n = to - from + 1;
            double[] x = new double[n];
            double[] y = new double[n];
            for (int i = 0; i < n; i++) {
                x[i] = from + i;
                y[i] = profile[from + i];
            }
            fit = levenbergMarquardt(x, y, fit);
            result.r2[d] = rSquared(x, y, fit);
            centre = fit[1];
            width = FWHM_PER_SIGMA * Math.abs(fit[2]);
            if (!Double.isFinite(centre) || !Double.isFinite(width) || width <= 0) {
                throw new IllegalArgumentException("the " + AXIS[d] + " fit did not converge");
            }
        }
        fit[2] = Math.abs(fit[2]); // sigma and -sigma fit equally well
        return fit;
    }

    /**
     * Distance between the two half-maximum crossings surrounding {@code peakIndex}, by linear
     * interpolation between the bracketing samples. Model-free, and therefore a good seed - and a
     * good sanity check on the fitted FWHM.
     */
    private static double halfMaximumWidth(double[] profile, int peakIndex,
                                           double background, double amplitude) {
        double half = background + amplitude / 2.0;
        double left = crossing(profile, peakIndex, -1, half);
        double right = crossing(profile, peakIndex, +1, half);
        double width = right - left;
        // A peak that never comes back down on one side (a profile clipped by the image border)
        // yields the whole remaining length; better to mirror the side that did cross.
        return width > 0 ? width : 1;
    }

    /** Sub-sample position where {@code profile} last crosses {@code half} walking from the peak. */
    private static double crossing(double[] profile, int peakIndex, int step, double half) {
        int i = peakIndex;
        while (i + step >= 0 && i + step < profile.length && profile[i + step] > half) i += step;
        int j = i + step;
        if (j < 0 || j >= profile.length) return i; // never crosses: stop at the edge
        double f = (profile[i] - half) / (profile[i] - profile[j]);
        return i + step * f;
    }

    /** Four-parameter Gaussian-plus-offset fit, with the Jacobian given analytically. */
    private static double[] levenbergMarquardt(double[] x, double[] y, double[] start) {
        MultivariateJacobianFunction model = point -> {
            double a = point.getEntry(0);
            double mu = point.getEntry(1);
            double sigma = point.getEntry(2);
            double b = point.getEntry(3);
            RealVector value = new ArrayRealVector(x.length);
            RealMatrix jacobian = new Array2DRowRealMatrix(x.length, 4);
            for (int i = 0; i < x.length; i++) {
                double dx = x[i] - mu;
                double e = Math.exp(-dx * dx / (2 * sigma * sigma));
                value.setEntry(i, a * e + b);
                jacobian.setEntry(i, 0, e);
                jacobian.setEntry(i, 1, a * e * dx / (sigma * sigma));
                jacobian.setEntry(i, 2, a * e * dx * dx / (sigma * sigma * sigma));
                jacobian.setEntry(i, 3, 1);
            }
            return new Pair<>(value, jacobian);
        };
        LeastSquaresProblem problem = new LeastSquaresBuilder()
                .start(start)
                .model(model)
                .target(y)
                .lazyEvaluation(false)
                .maxEvaluations(1000)
                .maxIterations(1000)
                .build();
        return new LevenbergMarquardtOptimizer().optimize(problem).getPoint().toArray();
    }

    /** Coefficient of determination of the fit over the window it was computed on. */
    private static double rSquared(double[] x, double[] y, double[] fit) {
        double mean = 0;
        for (double v : y) mean += v;
        mean /= y.length;
        double residual = 0;
        double total = 0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - fit[1];
            double model = fit[0] * Math.exp(-dx * dx / (2 * fit[2] * fit[2])) + fit[3];
            residual += (y[i] - model) * (y[i] - model);
            total += (y[i] - mean) * (y[i] - mean);
        }
        return total > 0 ? 1 - residual / total : Double.NaN;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]);
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

    /** {@code "a, b, c"} - the three axes of one measurement, in one table cell. */
    private static String triplet(double[] values, String format, String separator) {
        return String.format(format, values[0]) + separator + String.format(format, values[1])
                + separator + String.format(format, values[2]);
    }
}
