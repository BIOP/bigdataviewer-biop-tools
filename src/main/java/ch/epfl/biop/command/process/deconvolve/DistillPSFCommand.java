/*-
 * #%L
 * Tiled GPU Deconvolution for BigDataViewer-Playground - BIOP - EPFL
 * %%
 * Copyright (C) 2024 - 2025 EPFL
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package ch.epfl.biop.command.process.deconvolve;

import bdv.cache.SharedQueue;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.deconvolve.Deconvolver;
import ij.IJ;
import ij.Prefs;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clijx.CLIJx;
import net.haesleinhuepf.clijx.parallel.CLIJPoolOptions;
import net.haesleinhuepf.clijx.parallel.CLIJxPool;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Distills a point spread function (PSF) from a bead image and a mask of bead
 * centres, using the same GPU Richardson-Lucy engine as the deconvolution command
 * but with the roles of image and kernel swapped ("reverse deconvolution").
 * See {@link Deconvolver#distillPSF} for the rationale.
 * <p>
 * Unlike the tiled deconvolution command, distillation processes the <b>whole
 * volume as a single tile</b>, because the point mask (which plays the role of the
 * "PSF") is non-local and spans the entire image. As a consequence the FFT is
 * computed on a volume padded to roughly twice the image size along every axis and
 * requires a large amount of GPU memory - typically an order of magnitude more than
 * a plain float copy of the input.
 * </p>
 * <p>
 * Because of this, the multi-GPU / multi-context {@link CLIJxPool} used for tiled
 * throughput is counter-productive here: there is a single, huge tile that needs a
 * whole GPU to itself. This command therefore temporarily shuts down the shared pool,
 * runs the distillation on a single context on the chosen device (maximising free
 * VRAM), and restores the original pool afterwards in a {@code finally} block.
 * </p>
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        initializer = "init",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ProcessMenu, weight = BdvPgMenus.ProcessW),
                @Menu(label = "Deconvolve", weight = -1.1),
                @Menu(label = "Source - Distill PSF (Richardson Lucy GPU)", weight = 7)
        },
        description = "Distills a PSF from a bead image and a mask of bead centres, using the " +
                "GPU Richardson-Lucy engine (CLIJ2). Processes the whole volume as a single tile, " +
                "so it needs a full GPU and a lot of VRAM.")
public class DistillPSFCommand implements BdvPlaygroundActionCommand {

    /** Rough multiplier: peak VRAM as a number of full extended float buffers. */
    private static final int PEAK_BUFFERS_LOW = 10;
    private static final int PEAK_BUFFERS_HIGH = 13;

    @Parameter
    OpService ops;

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String pool_message = "";

    @Parameter(label = "Bead Image Source",
            description = "The 3D image containing sub-resolution beads",
            callback = "updateEstimate")
    SourceAndConverter<?> beads;

    @Parameter(label = "Bead Centres (Point Mask) Source",
            description = "A same-sized image with a single non-zero pixel at the centre of each bead",
            callback = "updateEstimate")
    SourceAndConverter<?> point_mask;

    @Parameter(label = "GPU Device Index",
            description = "Index of the GPU device the distillation will run on (see list above)",
            callback = "updateEstimate")
    int device_index = 0;

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String ram_message = "Select both sources to estimate the required GPU RAM.";

    @Parameter(label = "Name",
            description = "Name of the distilled PSF output source")
    String name = "distilled_psf";

    @Parameter(label = "Iterations",
            description = "Number of Richardson-Lucy iterations")
    int num_iterations = 10;

    @Parameter(label = "Non-Circulant",
            description = "When checked, uses non-circulant boundary conditions (reduces edge artifacts)")
    boolean non_circulant = false;

    @Parameter(label = "Regularization Factor",
            description = "Regularization strength to prevent noise amplification (0 = no regularization)")
    float regularization_factor = 0f;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The distilled PSF source")
    SourceAndConverter<?> psf_out;

    /** Builds the informational message shown at the top of the dialog. */
    protected void init() {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("<h3>PSF distillation - GPU usage</h3>");
        sb.append("This runs as a <b>single, whole-image tile</b> and needs a full GPU.<br>");
        sb.append("The shared CLIJ pool will be <b>temporarily shut down</b>, distillation will run<br>");
        sb.append("on a single context on the device below, and the pool will be <b>restored</b> afterwards.<br><br>");
        try {
            ArrayList<String> devices = CLIJ.getAvailableDeviceNames();
            sb.append("Available devices:<br>");
            for (int i = 0; i < devices.size(); i++) {
                sb.append("&nbsp;&nbsp;- [").append(i).append("] ").append(devices.get(i)).append("<br>");
            }
        } catch (Exception e) {
            sb.append("<font color=red>Could not query OpenCL devices - the GPU may be unavailable.</font><br>");
        }
        sb.append("Current shared pool spec: <b>").append(Prefs.get(CLIJPoolOptions.KEY, "0:1")).append("</b>");
        sb.append("</html>");
        pool_message = sb.toString();
    }

    /** Recomputes the estimated GPU RAM whenever the sources or device change. */
    protected void updateEstimate() {
        try {
            if (beads == null || point_mask == null) {
                ram_message = "Select both sources to estimate the required GPU RAM.";
                return;
            }
            long[] beadDims = beads.getSpimSource().getSource(0, 0).dimensionsAsLongArray();
            long[] maskDims = point_mask.getSpimSource().getSource(0, 0).dimensionsAsLongArray();

            if (beadDims.length < 3 || maskDims.length < 3) {
                ram_message = "<html><font color=red>PSF distillation requires 3D sources.</font></html>";
                return;
            }
            if (!Arrays.equals(beadDims, maskDims)) {
                ram_message = "<html><font color=red>Bead " + Arrays.toString(beadDims)
                        + " and mask " + Arrays.toString(maskDims)
                        + " dimensions must match.</font></html>";
                return;
            }

            long[] ext = extendedFFTDimensions(beadDims, maskDims);
            long oneBuffer = ext[0] * ext[1] * ext[2] * 4L;

            ram_message = "<html>"
                    + "Padded FFT volume: <b>" + ext[0] + " x " + ext[1] + " x " + ext[2] + "</b>"
                    + " (input was " + beadDims[0] + " x " + beadDims[1] + " x " + beadDims[2] + ")<br>"
                    + "One float buffer at that size: <b>" + gb(oneBuffer) + "</b><br>"
                    + "Estimated peak GPU RAM: <b>" + gb((long) PEAK_BUFFERS_LOW * oneBuffer)
                    + " - " + gb((long) PEAK_BUFFERS_HIGH * oneBuffer) + "</b> (rough)"
                    + "</html>";
        } catch (Exception e) {
            ram_message = "<html>Could not estimate GPU RAM: " + e.getMessage() + "</html>";
        }
    }

    /** Extended (padded, next-fast-FFT) dimensions, matching what the CLIJ2-FFT engine uses. */
    private long[] extendedFFTDimensions(long[] imageDims, long[] psfDims) {
        FinalDimensions pre = new FinalDimensions(
                imageDims[0] + psfDims[0],
                imageDims[1] + psfDims[1],
                imageDims[2] + psfDims[2]);
        long[][] fast = ops.filter().fftSize(pre, false);
        return new long[]{fast[0][0], fast[0][1], fast[0][2]};
    }

    private static String gb(long bytes) {
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    @Override
    public void run() {
        // --- Validation (before touching the pool) ---
        if (!point_mask.getSpimSource().isPresent(0)) {
            IJ.error("PSF distillation", "The point mask must be defined at timepoint 0.");
            return;
        }
        if (!(point_mask.getSpimSource().getType() instanceof RealType)) {
            IJ.error("PSF distillation", "The point mask pixel type "
                    + point_mask.getSpimSource().getType().getClass().getSimpleName()
                    + " cannot be used for PSF distillation.");
            return;
        }
        long[] beadDims = beads.getSpimSource().getSource(0, 0).dimensionsAsLongArray();
        long[] maskDims = point_mask.getSpimSource().getSource(0, 0).dimensionsAsLongArray();
        if (beadDims.length < 3 || !Arrays.equals(beadDims, maskDims)) {
            IJ.error("PSF distillation", "Bead and mask must be 3D and have identical dimensions.\n"
                    + "Bead: " + Arrays.toString(beadDims) + "\nMask: " + Arrays.toString(maskDims));
            return;
        }

        int nDevices;
        try {
            nDevices = CLIJ.getAvailableDeviceNames().size();
        } catch (Exception e) {
            IJ.error("PSF distillation", "No OpenCL device available: " + e.getMessage());
            return;
        }
        if (device_index < 0 || device_index >= nDevices) {
            IJ.error("PSF distillation", "GPU device index " + device_index
                    + " does not exist (found " + nDevices + " device(s)).");
            return;
        }

        // --- Pool swap + distillation, restoring the pool no matter what ---
        final String originalSpec = Prefs.get(CLIJPoolOptions.KEY, "0:1");
        final boolean hadPool = CLIJxPool.isIntanceSet();
        CLIJxPool singlePool = null;
        try {
            if (hadPool) {
                IJ.log("[Distill PSF] Temporarily shutting down the shared CLIJ pool to free the GPU...");
                CLIJxPool.getInstance().shutdown();
            }
            singlePool = new CLIJxPool(new int[]{device_index}, new int[]{1});
            CLIJxPool.setInstance(singlePool);

            preflightMemoryCheck(singlePool, beadDims, maskDims);

            // Build the (lazy) distilled-PSF source, then force it to compute now, on this
            // thread, so the GPU work happens inside this try block while the single-GPU pool
            // is the active one.
            psf_out = Deconvolver.distillPSF(
                    (SourceAndConverter) beads,
                    (SourceAndConverter) point_mask,
                    name,
                    num_iterations,
                    non_circulant,
                    regularization_factor,
                    new SharedQueue(1, 1));

            double sum = forceCompute(psf_out);
            if (sum == 0.0) {
                IJ.log("[Distill PSF] WARNING: the distilled PSF is all zeros. The GPU computation "
                        + "likely failed (out of memory?). Check the console/log for a stack trace.");
            } else {
                IJ.log("[Distill PSF] Done.");
            }
        } finally {
            // Shut down the single-GPU pool used for distillation...
            if (singlePool != null) {
                try {
                    singlePool.shutdown();
                } catch (Exception e) {
                    IJ.log("[Distill PSF] Error shutting down the distillation pool: " + e.getMessage());
                }
            }
            // ...and restore the user's original pool.
            if (hadPool) {
                try {
                    int[][] specs = CLIJPoolOptions.parseDeviceThreads(originalSpec);
                    CLIJxPool.setInstance(new CLIJxPool(specs[0], specs[1]));
                    IJ.log("[Distill PSF] Restored the shared CLIJ pool (" + originalSpec + ").");
                } catch (Exception e) {
                    IJ.log("[Distill PSF] Could not restore the shared CLIJ pool (" + originalSpec
                            + "): " + e.getMessage() + ". It will be recreated on next use.");
                }
            }
        }
    }

    /** Warns (but does not abort) if the estimate exceeds the device's memory limits. */
    private void preflightMemoryCheck(CLIJxPool pool, long[] beadDims, long[] maskDims) {
        try {
            long[] ext = extendedFFTDimensions(beadDims, maskDims);
            long oneBuffer = ext[0] * ext[1] * ext[2] * 4L;
            long peakHigh = (long) PEAK_BUFFERS_HIGH * oneBuffer;

            CLIJx clijx = pool.getIdleCLIJx();
            long globalMem, maxAlloc;
            try {
                globalMem = clijx.getCLIJ().getClearCLContext().getDevice().getGlobalMemorySizeInBytes();
                maxAlloc = clijx.getCLIJ().getClearCLContext().getDevice().getMaxMemoryAllocationSizeInBytes();
            } finally {
                pool.setCLIJxIdle(clijx);
            }

            IJ.log("[Distill PSF] Padded FFT volume: " + ext[0] + " x " + ext[1] + " x " + ext[2]
                    + " (" + gb(oneBuffer) + " per float buffer).");
            IJ.log("[Distill PSF] Estimated peak GPU RAM ~" + gb((long) PEAK_BUFFERS_LOW * oneBuffer)
                    + " - " + gb(peakHigh) + ". Device: total " + gb(globalMem)
                    + ", max single allocation " + gb(maxAlloc) + ".");

            if (peakHigh > globalMem) {
                IJ.log("[Distill PSF] WARNING: estimated peak (" + gb(peakHigh)
                        + ") exceeds total GPU memory (" + gb(globalMem)
                        + "). The run may fail with an out-of-memory error.");
            }
            if (oneBuffer > maxAlloc) {
                IJ.log("[Distill PSF] WARNING: a single FFT buffer (" + gb(oneBuffer)
                        + ") exceeds the device's max single allocation (" + gb(maxAlloc)
                        + "). The run will very likely fail.");
            }
        } catch (Exception e) {
            IJ.log("[Distill PSF] Could not run the GPU memory pre-flight check: " + e.getMessage());
        }
    }

    /**
     * Forces synchronous computation of the (single-tile) lazy source on the current thread
     * and returns the sum of all voxels, so we can detect an all-zero (failed) result.
     */
    @SuppressWarnings("unchecked")
    private static double forceCompute(SourceAndConverter<?> sac) {
        RandomAccessibleInterval<FloatType> rai =
                (RandomAccessibleInterval<FloatType>) sac.getSpimSource().getSource(0, 0);
        double sum = 0.0;
        Cursor<FloatType> c = Views.iterable(rai).cursor();
        while (c.hasNext()) {
            sum += c.next().getRealDouble();
        }
        return sum;
    }
}