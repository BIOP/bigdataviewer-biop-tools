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
import bdv.util.RandomAccessibleIntervalSource;
import bdv.viewer.Source;
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
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * Cached {@code {globalMemBytes, maxAllocBytes}} per GPU device index, so the live auto-crop
     * estimate is GPU-call-free after the first query. Refreshed with authoritative values at run time.
     */
    private static final Map<Integer, long[]> DEVICE_MEMORY = new ConcurrentHashMap<>();

    @Parameter
    OpService ops;

    @Parameter(required = false)
    TaskService taskService;

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String pool_message = "";

    @Parameter(label = "Bead Image Source(s)",
            style = "sorted",
            description = "One or more 3D images containing sub-resolution beads (e.g. the channels of a "
                    + "multi-channel acquisition). Each is distilled sequentially against the same point mask.",
            callback = "updateEstimate")
    SourceAndConverter<?>[] beads;

    @Parameter(label = "Bead Centres (Point Mask) Source",
            description = "A same-sized image with a single non-zero pixel at the centre of each bead",
            callback = "updateEstimate")
    SourceAndConverter<?> point_mask;

    @Parameter(label = "GPU Device Index",
            description = "Index of the GPU device the distillation will run on (see list above)",
            callback = "updateEstimate")
    int device_index = 0;

    @Parameter(label = "Auto-crop XY to fit GPU memory",
            description = "Automatically crop the bead image and mask in XY (centred) to the largest size that " +
                    "fits the GPU, based on the memory fraction below. Z is left at full size by default.",
            callback = "updateEstimate")
    boolean auto_crop_xy = false;

    @Parameter(label = "GPU Memory Fraction",
            description = "Fraction of the GPU's total memory the padded-FFT peak is allowed to reach (0.1 - 1.0). " +
                    "Lower is safer. Only used when auto-crop is enabled.",
            min = "0.1", max = "1.0", stepSize = "0.05", style = "slider",
            callback = "updateEstimate")
    double gpu_memory_fraction = 0.8;

    @Parameter(label = "Also auto-crop Z (not recommended)",
            description = "By default only XY is cropped. Enable to also shrink Z (isotropically with XY) if the " +
                    "volume still does not fit - this truncates the PSF along the optical axis.",
            callback = "updateEstimate")
    boolean auto_crop_z = false;

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

    @Parameter(label = "Crop Output Around Centre",
            description = "Crop the distilled PSF, which sits at the centre of the volume, to the size below")
    boolean crop_output = true;

    @Parameter(label = "PSF Size X", description = "Cropped PSF width (clamped to the image size)")
    int psf_size_x = 64;

    @Parameter(label = "PSF Size Y", description = "Cropped PSF height (clamped to the image size)")
    int psf_size_y = 64;

    @Parameter(label = "PSF Size Z", description = "Cropped PSF depth (clamped to the image size, never zero-padded)")
    int psf_size_z = 128;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The distilled PSF sources, one per input bead source (same order)")
    SourceAndConverter<?>[] psf_out;

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
            if (beads == null || beads.length == 0 || point_mask == null) {
                ram_message = "Select bead source(s) and the point mask to estimate the required GPU RAM.";
                return;
            }
            long[] beadDims = beads[0].getSpimSource().getSource(0, 0).dimensionsAsLongArray();
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

            StringBuilder sb = new StringBuilder("<html>");
            sb.append("<b>").append(beads.length).append("</b> bead source(s) - processed <b>sequentially</b>, ")
                    .append("so the estimate below applies to each channel in turn (not summed).<br>");

            // Dimensions that will actually be sent to the GPU (possibly auto-cropped).
            long[] procDims = beadDims;
            long[] mem = auto_crop_xy ? deviceMemory(device_index) : null;
            if (auto_crop_xy) {
                if (mem == null) {
                    sb.append("<font color=red>Auto-crop is on but device ").append(device_index)
                            .append("'s memory could not be queried here; the crop will be computed at run time.</font><br>");
                } else {
                    long[] crop = computeAutoCrop(beadDims, mem[0], mem[1], gpu_memory_fraction, auto_crop_z);
                    procDims = crop;
                    boolean cropped = crop[0] != beadDims[0] || crop[1] != beadDims[1] || crop[2] != beadDims[2];
                    sb.append("Auto-crop (device ").append(device_index).append(", ")
                            .append(String.format("%.0f%%", gpu_memory_fraction * 100)).append(" of ")
                            .append(gb(mem[0])).append(" = ").append(gb((long) (gpu_memory_fraction * mem[0])))
                            .append("): ");
                    if (cropped) {
                        sb.append("<b>").append(crop[0]).append(" x ").append(crop[1]).append(" x ").append(crop[2])
                                .append("</b> (from ").append(beadDims[0]).append(" x ").append(beadDims[1])
                                .append(" x ").append(beadDims[2]).append(")<br>");
                    } else {
                        sb.append("<b>none needed</b> - the full volume fits.<br>");
                    }
                }
            }

            long[] ext = extendedFFTDimensions(procDims, procDims);
            long oneBuffer = ext[0] * ext[1] * ext[2] * 4L;
            sb.append("Padded FFT volume: <b>").append(ext[0]).append(" x ").append(ext[1]).append(" x ")
                    .append(ext[2]).append("</b>");
            if (procDims != beadDims) {
                sb.append(" (from cropped ").append(procDims[0]).append(" x ").append(procDims[1])
                        .append(" x ").append(procDims[2]).append(")");
            } else {
                sb.append(" (input ").append(beadDims[0]).append(" x ").append(beadDims[1]).append(" x ")
                        .append(beadDims[2]).append(")");
            }
            sb.append("<br>One float buffer at that size: <b>").append(gb(oneBuffer)).append("</b><br>")
                    .append("Estimated peak GPU RAM: <b>").append(gb((long) PEAK_BUFFERS_LOW * oneBuffer))
                    .append(" - ").append(gb((long) PEAK_BUFFERS_HIGH * oneBuffer)).append("</b> (rough)");
            if (mem != null) {
                sb.append("<br>Device total memory: <b>").append(gb(mem[0])).append("</b>");
            }
            sb.append("</html>");
            ram_message = sb.toString();
        } catch (Exception e) {
            ram_message = "<html>Could not estimate GPU RAM: " + e.getMessage() + "</html>";
        }
    }

    /**
     * Returns {@code {globalMemBytes, maxAllocBytes}} for the given GPU device, or {@code null} if it
     * cannot be queried. Cached in {@link #DEVICE_MEMORY} so the live estimate does not open a GPU
     * context on every dialog interaction.
     */
    private static long[] deviceMemory(int deviceIndex) {
        long[] cached = DEVICE_MEMORY.get(deviceIndex);
        if (cached != null) return cached;
        try {
            CLIJx clijx = new CLIJx(new CLIJ(deviceIndex));
            try {
                long global = clijx.getCLIJ().getClearCLContext().getDevice().getGlobalMemorySizeInBytes();
                long maxAlloc = clijx.getCLIJ().getClearCLContext().getDevice().getMaxMemoryAllocationSizeInBytes();
                long[] mem = {global, maxAlloc};
                DEVICE_MEMORY.put(deviceIndex, mem);
                return mem;
            } finally {
                clijx.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Largest centred crop whose estimated peak GPU RAM stays within {@code fraction} of the device's
     * global memory (and whose single FFT buffer stays under {@code maxAlloc}). XY is always scaled;
     * Z is scaled together with XY only when {@code cropZ} is true, otherwise it is kept at full size.
     * Returns {@code {cropX, cropY, cropZ}}; equals {@code fullDims} when no cropping is required.
     */
    private long[] computeAutoCrop(long[] fullDims, long globalMem, long maxAlloc, double fraction, boolean cropZ) {
        double budget = fraction * globalMem;
        if (fitsBudget(fullDims, budget, maxAlloc)) {
            return new long[]{fullDims[0], fullDims[1], fullDims[2]};
        }
        // feasible(s) is monotonic: larger scale -> larger padded FFT -> more memory, so binary-search s.
        double lo = 0.0, hi = 1.0;
        for (int it = 0; it < 40; it++) {
            double mid = 0.5 * (lo + hi);
            if (fitsBudget(scaledCrop(fullDims, mid, cropZ), budget, maxAlloc)) lo = mid; else hi = mid;
        }
        return scaledCrop(fullDims, lo, cropZ);
    }

    /** Centred crop dimensions for a scale factor, rounded to even sizes and clamped to the full size. */
    private static long[] scaledCrop(long[] fullDims, double s, boolean cropZ) {
        return new long[]{
                clampEven(Math.round(fullDims[0] * s), fullDims[0]),
                clampEven(Math.round(fullDims[1] * s), fullDims[1]),
                cropZ ? clampEven(Math.round(fullDims[2] * s), fullDims[2]) : fullDims[2]
        };
    }

    private static long clampEven(long v, long full) {
        if (v < 2) v = 2;
        if (v > full) v = full;
        if ((v & 1L) == 1L) v = Math.min(v + 1, full); // prefer even sizes, but never exceed the full size
        return v;
    }

    /** True if the padded FFT of {@code dims} (used as both image and kernel) fits the memory limits. */
    private boolean fitsBudget(long[] dims, double budget, long maxAlloc) {
        long[] ext = extendedFFTDimensions(dims, dims);
        long oneBuffer = ext[0] * ext[1] * ext[2] * 4L;
        return (double) PEAK_BUFFERS_HIGH * oneBuffer <= budget && oneBuffer <= maxAlloc;
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
        if (beads == null || beads.length == 0) {
            IJ.error("PSF distillation", "No bead source selected.");
            return;
        }
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
        long[] maskDims = point_mask.getSpimSource().getSource(0, 0).dimensionsAsLongArray();
        // All bead sources are distilled against the same point mask, so they must all share its dimensions.
        for (int ch = 0; ch < beads.length; ch++) {
            long[] beadDims = beads[ch].getSpimSource().getSource(0, 0).dimensionsAsLongArray();
            if (beadDims.length < 3 || !Arrays.equals(beadDims, maskDims)) {
                IJ.error("PSF distillation", "Every bead source must be 3D and have the same dimensions as the "
                        + "point mask.\nBead source #" + ch + " (" + beads[ch].getSpimSource().getName() + "): "
                        + Arrays.toString(beadDims) + "\nMask: " + Arrays.toString(maskDims));
                return;
            }
        }
        final long[] beadDims = maskDims; // identical for every channel (validated above)

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
        Task task = null;
        final List<SourceAndConverter<?>> outputs = new ArrayList<>();
        try {
            if (taskService != null) {
                task = taskService.createTask("Distill PSF (" + beads.length + " channel(s))");
                task.setProgressMaximum(beads.length);
            }

            if (hadPool) {
                IJ.log("[Distill PSF] Temporarily shutting down the shared CLIJ pool to free the GPU...");
                CLIJxPool.getInstance().shutdown();
            }
            singlePool = new CLIJxPool(new int[]{device_index}, new int[]{1});
            CLIJxPool.setInstance(singlePool);

            // Authoritative device memory from the pool we are about to run on (also refreshes the cache).
            long[] mem = poolDeviceMemory(singlePool);
            if (mem != null) DEVICE_MEMORY.put(device_index, mem);

            // Optionally auto-crop XY (and Z) so the padded FFT fits the GPU. The mask is cropped once
            // (it is shared by every channel); each bead source is cropped to the same centred window.
            SourceAndConverter<?> workingMask = point_mask;
            long[] cropDims = null;
            if (auto_crop_xy) {
                if (mem != null) {
                    long[] crop = computeAutoCrop(beadDims, mem[0], mem[1], gpu_memory_fraction, auto_crop_z);
                    if (crop[0] != beadDims[0] || crop[1] != beadDims[1] || crop[2] != beadDims[2]) {
                        cropDims = crop;
                        IJ.log("[Distill PSF] Auto-crop to " + crop[0] + " x " + crop[1] + " x " + crop[2]
                                + " (from " + beadDims[0] + " x " + beadDims[1] + " x " + beadDims[2] + ") to fit "
                                + String.format("%.0f%%", gpu_memory_fraction * 100) + " of " + gb(mem[0]) + ".");
                        if (crop_output && (crop[0] < psf_size_x || crop[1] < psf_size_y)) {
                            IJ.log("[Distill PSF] NOTE: the auto-cropped XY (" + crop[0] + " x " + crop[1]
                                    + ") is smaller than the requested PSF crop (" + psf_size_x + " x " + psf_size_y
                                    + "); the output PSF will be correspondingly smaller.");
                        }
                        workingMask = cropSourceCentered(point_mask, cropDims);
                    } else {
                        IJ.log("[Distill PSF] Auto-crop enabled but the full volume already fits; no cropping applied.");
                    }
                } else {
                    IJ.log("[Distill PSF] Auto-crop requested but device memory could not be queried; running un-cropped.");
                }
            }

            long[] effDims = cropDims != null ? cropDims : beadDims;
            preflightMemoryCheck(mem, effDims);

            // Each bead source is distilled sequentially, so at most one full-image FFT lives on the
            // GPU at a time and the memory estimate above applies per channel.
            for (int ch = 0; ch < beads.length; ch++) {
                // Cancellation is honoured only between channels, never during a GPU deconvolution.
                if (task != null && task.isCanceled()) {
                    IJ.log("[Distill PSF] Cancelled by user after " + ch + " / " + beads.length + " channel(s).");
                    break;
                }

                SourceAndConverter<?> beadSource = beads[ch];
                String channelName = beads.length == 1 ? name : name + "_ch" + ch;
                if (task != null) {
                    task.setStatusMessage("Distilling PSF - channel " + (ch + 1) + " / " + beads.length
                            + " (" + beadSource.getSpimSource().getName() + ")");
                }
                IJ.log("[Distill PSF] Channel " + (ch + 1) + " / " + beads.length + " - "
                        + beadSource.getSpimSource().getName());

                // Apply the same centred crop to this channel's beads (the mask was cropped above).
                SourceAndConverter<?> workingBeads = cropDims != null
                        ? cropSourceCentered(beadSource, cropDims) : beadSource;

                // Build the (lazy) distilled-PSF source, then force it to compute now, on this
                // thread, so the GPU work happens inside this try block while the single-GPU pool
                // is the active one.
                SourceAndConverter<?> fullPsf = Deconvolver.distillPSF(
                        (SourceAndConverter) workingBeads,
                        (SourceAndConverter) workingMask,
                        channelName,
                        num_iterations,
                        non_circulant,
                        regularization_factor,
                        new SharedQueue(1, 1));

                double sum = forceCompute(fullPsf);
                if (sum == 0.0) {
                    IJ.log("[Distill PSF] WARNING: the distilled PSF for channel " + (ch + 1)
                            + " is all zeros. The GPU computation likely failed (out of memory?). "
                            + "Check the console/log for a stack trace.");
                }

                // The distilled PSF sits at the centre of the volume; crop around it if requested.
                if (crop_output) {
                    outputs.add(cropCentered(fullPsf, beadSource,
                            new long[]{psf_size_x, psf_size_y, psf_size_z}, channelName));
                } else {
                    outputs.add(fullPsf);
                }

                if (task != null) task.setProgressValue(ch + 1);
            }
            IJ.log("[Distill PSF] Done (" + outputs.size() + " / " + beads.length + " channel(s)).");
        } finally {
            psf_out = outputs.toArray(new SourceAndConverter[0]);
            if (task != null) task.finish();
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

    /** Reads {@code {globalMemBytes, maxAllocBytes}} from an idle instance of the pool, or {@code null} on failure. */
    private static long[] poolDeviceMemory(CLIJxPool pool) {
        try {
            CLIJx clijx = pool.getIdleCLIJx();
            try {
                long global = clijx.getCLIJ().getClearCLContext().getDevice().getGlobalMemorySizeInBytes();
                long maxAlloc = clijx.getCLIJ().getClearCLContext().getDevice().getMaxMemoryAllocationSizeInBytes();
                return new long[]{global, maxAlloc};
            } finally {
                pool.setCLIJxIdle(clijx);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Warns (but does not abort) if the estimate for {@code dims} exceeds the device's memory limits. */
    private void preflightMemoryCheck(long[] mem, long[] dims) {
        try {
            long[] ext = extendedFFTDimensions(dims, dims);
            long oneBuffer = ext[0] * ext[1] * ext[2] * 4L;
            long peakHigh = (long) PEAK_BUFFERS_HIGH * oneBuffer;

            IJ.log("[Distill PSF] Padded FFT volume: " + ext[0] + " x " + ext[1] + " x " + ext[2]
                    + " (" + gb(oneBuffer) + " per float buffer).");
            if (mem == null) {
                IJ.log("[Distill PSF] Estimated peak GPU RAM ~" + gb((long) PEAK_BUFFERS_LOW * oneBuffer)
                        + " - " + gb(peakHigh) + ". (Device memory could not be queried.)");
                return;
            }
            long globalMem = mem[0], maxAlloc = mem[1];
            IJ.log("[Distill PSF] Estimated peak GPU RAM ~" + gb((long) PEAK_BUFFERS_LOW * oneBuffer)
                    + " - " + gb(peakHigh) + ". Device: total " + gb(globalMem)
                    + ", max single allocation " + gb(maxAlloc) + ".");

            if (peakHigh > globalMem) {
                IJ.log("[Distill PSF] WARNING: estimated peak (" + gb(peakHigh)
                        + ") exceeds total GPU memory (" + gb(globalMem)
                        + "). The run may fail with an out-of-memory error"
                        + (auto_crop_xy ? "" : " - consider enabling auto-crop XY") + ".");
            }
            if (oneBuffer > maxAlloc) {
                IJ.log("[Distill PSF] WARNING: a single FFT buffer (" + gb(oneBuffer)
                        + ") exceeds the device's max single allocation (" + gb(maxAlloc)
                        + "). The run will very likely fail"
                        + (auto_crop_xy ? "" : " - consider enabling auto-crop XY") + ".");
            }
        } catch (Exception e) {
            IJ.log("[Distill PSF] Could not run the GPU memory pre-flight check: " + e.getMessage());
        }
    }

    /**
     * Crops any source to a centred box of the given size (per-axis clamped to the source, never
     * zero-padded), preserving its pixel type and physical voxel calibration. This is plain imglib2
     * ({@link Views#interval}/{@link Views#zeroMin}) re-wrapped as a {@link RandomAccessibleIntervalSource}
     * whose transform is shifted so the crop keeps its original physical location.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SourceAndConverter<?> cropSourceCentered(SourceAndConverter<?> src, long[] size) {
        Source<?> spim = src.getSpimSource();
        RandomAccessibleInterval rai = spim.getSource(0, 0);
        long[] dims = rai.dimensionsAsLongArray();

        long[] min = new long[3];
        long[] max = new long[3];
        for (int d = 0; d < 3; d++) {
            long crop = Math.min(size[d], dims[d]);
            min[d] = rai.min(d) + (dims[d] - crop) / 2;
            max[d] = min[d] + crop - 1;
        }

        RandomAccessibleInterval cropped = Views.zeroMin(Views.interval(rai, min, max));

        AffineTransform3D transform = new AffineTransform3D();
        spim.getSourceTransform(0, 0, transform);
        double[] worldMin = new double[3];
        transform.apply(new double[]{min[0], min[1], min[2]}, worldMin);
        AffineTransform3D croppedTransform = transform.copy();
        croppedTransform.setTranslation(worldMin);

        NumericType type = (NumericType) Util.getTypeFromInterval(rai);
        Source cropSource = new RandomAccessibleIntervalSource(cropped, type, croppedTransform, spim.getName());
        return SourceHelper.createSourceAndConverter(cropSource);
    }

    /**
     * Crops the (already computed) full-size PSF around the centre of the volume to at most the
     * requested size, clamping each axis to the available size (never zero-padding). The output
     * keeps the physical voxel calibration of the calibration source.
     */
    @SuppressWarnings("unchecked")
    private SourceAndConverter<FloatType> cropCentered(SourceAndConverter<?> fullSource,
                                                       SourceAndConverter<?> calibrationSource,
                                                       long[] requestedSize, String outputName) {
        RandomAccessibleInterval<FloatType> full =
                (RandomAccessibleInterval<FloatType>) fullSource.getSpimSource().getSource(0, 0);
        long[] fullDims = full.dimensionsAsLongArray();

        long[] min = new long[3];
        long[] max = new long[3];
        for (int d = 0; d < 3; d++) {
            long cropDim = Math.min(requestedSize[d], fullDims[d]);
            min[d] = full.min(d) + (fullDims[d] - cropDim) / 2;
            max[d] = min[d] + cropDim - 1;
        }

        RandomAccessibleInterval<FloatType> cropped = Views.zeroMin(Views.interval(full, min, max));
        IJ.log("[Distill PSF] Cropped PSF to " + (max[0] - min[0] + 1) + " x "
                + (max[1] - min[1] + 1) + " x " + (max[2] - min[2] + 1)
                + " around the centre (requested " + requestedSize[0] + " x " + requestedSize[1]
                + " x " + requestedSize[2] + ").");

        // Preserve the calibration source's voxel size/orientation, shifting the translation so the
        // cropped, zero-min PSF stays at the same physical location it occupied in the full volume.
        AffineTransform3D transform = new AffineTransform3D();
        calibrationSource.getSpimSource().getSourceTransform(0, 0, transform);
        double[] worldMin = new double[3];
        transform.apply(new double[]{min[0], min[1], min[2]}, worldMin);
        AffineTransform3D croppedTransform = transform.copy();
        croppedTransform.setTranslation(worldMin);

        Source<FloatType> src = new RandomAccessibleIntervalSource<>(
                cropped, new FloatType(), croppedTransform, outputName);
        return SourceHelper.createSourceAndConverter(src);
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