package ch.epfl.biop.scijava.command.spimdata;

import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import java.util.Arrays;

/**
 * Compensates Z-drift over time for LLS7 skewed acquisitions.
 * <p>
 * Algorithm:
 * 1. For each timepoint, extract the middle XZ plane (middle Y slice)
 * 2. Compute median projection along X to get a Z intensity profile
 * 3. Find the first Z position from the bottom where intensity exceeds threshold
 * 4. Calculate drift relative to first timepoint
 * 5. Apply Z translation to compensate
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu + "BDV>LLS7 - Compensate Z-drift",
        description = "Compensates Z-drift over time for LLS7 skewed acquisitions by tracking sample position")
public class LLS7ZDriftCompensationCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Model Source",
            description = "Source used to detect Z-drift (typically a bright, stable channel)")
    SourceAndConverter<?> model_source;

    @Parameter(label = "Sources to Correct",
            description = "All sources that will receive the Z-drift compensation transform")
    SourceAndConverter<?>[] sources_to_correct;

    @Parameter(label = "Intensity Threshold",
            description = "Intensity value above which the sample is considered present")
    double threshold = 100;

    int resolution_level = 0; // We always do that

    @Parameter(label = "Transform Mode",
            choices = {"Mutate", "Append"},
            description = "Mutate modifies existing transform; Append adds a new transform layer")
    String mode = "Append";

    @Parameter(label = "Debug Mode",
            description = "When enabled, displays the XZ planes used for drift detection")
    boolean debug = false;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The detected Z-drift values for each timepoint (in world coordinates)")
    double[] z_drift_values;

    @Override
    public void run() {
        // Get number of timepoints from the model source
        int nTimepoints = SourceAndConverterHelper.getMaxTimepoint(model_source) + 1;

        // Array to store detected Z positions for each timepoint
        double[] zPositions = new double[nTimepoints];
        z_drift_values = new double[nTimepoints];

        System.out.println("LLS7 Z-Drift Compensation: Processing " + nTimepoints + " timepoints...");

        // Step 1: Detect Z position for each timepoint
        for (int t = 0; t < nTimepoints; t++) {
            zPositions[t] = detectZPosition(model_source, t, resolution_level, threshold);
            System.out.println("  Timepoint " + t + ": Z position = " + zPositions[t]);
        }

        // Step 2: Calculate drift relative to first timepoint
        double referenceZ = zPositions[0];
        for (int t = 0; t < nTimepoints; t++) {
            z_drift_values[t] = zPositions[t] - referenceZ;
        }

        System.out.println("Z-drift values: " + Arrays.toString(z_drift_values));

        // Step 3: Apply Z-drift compensation to all sources
        for (SourceAndConverter<?> source : sources_to_correct) {
            applyZDriftCompensation(source, z_drift_values);
        }

        // Update displays
        SourceAndConverterServices.getBdvDisplayService().updateDisplays(sources_to_correct);

        System.out.println("Z-drift compensation applied to " + sources_to_correct.length + " sources.");
    }

    /**
     * Detects the Z position of the sample at a given timepoint.
     * Algorithm:
     * 1. Get the middle XZ plane (middle Y slice)
     * 2. Compute median along X for each Z
     * 3. Find first Z from bottom exceeding threshold
     */
    private <T extends RealType<T>> double detectZPosition(SourceAndConverter<?> source, int timepoint, int level, double threshold) {
        @SuppressWarnings("unchecked")
        RandomAccessibleInterval<T> rai = (RandomAccessibleInterval<T>) source.getSpimSource().getSource(timepoint, level);

        // Get dimensions
        long sizeX = rai.dimension(0);
        long sizeY = rai.dimension(1);
        long sizeZ = rai.dimension(2);

        if (debug) {
            System.out.println("  Debug: RAI dimensions = [" + sizeX + ", " + sizeY + ", " + sizeZ + "]");
        }

        // Get the middle Y slice (XZ plane)
        long middleZ = sizeZ / 2;
        RandomAccessibleInterval<T> xzPlane = Views.hyperSlice(rai, 2, middleZ);

        // For each Z, compute median of X values
        float[] medianProfile = new float[(int) sizeY];

        for (int y = 0; y < sizeY; y++) {
            // Get the line at this Z (all X values)
            RandomAccessibleInterval<T> xLine = Views.hyperSlice(xzPlane, 1, y);

            // Collect all values for median calculation
            float[] xValues = new float[(int) sizeX];
            Cursor<T> cursor = Views.flatIterable(xLine).cursor();
            int i = 0;
            while (cursor.hasNext()) {
                cursor.fwd();
                xValues[i++] = cursor.get().getRealFloat();
            }

            // Compute median
            medianProfile[y] = computeMedian(xValues);
        }

        // Debug: show the XZ plane
        if (debug) {
            FloatProcessor fp = new FloatProcessor((int) sizeY, 1, medianProfile);
            ImagePlus debugImp = new ImagePlus("XZ median profile t=" + timepoint + " (middleZ=" + middleZ + ")", fp);
            //ImageJFunctions.wrap(xzPlane, "XZ median profile t=" + timepoint + " (middleZ=" + middleZ + ")");
            debugImp.show();
        }

        // Find first Z from bottom exceeding threshold
        int detectedY = 0;
        for (int y = (int) sizeY-1; y >=0; y--) {
            if (medianProfile[y] > threshold) {
                detectedY = y;
                break;
            }
        }

        if (debug) {
            // Print first and last few median values
            int printN = Math.min(10, (int) sizeZ);
            System.out.println("  Debug: Median profile (first " + printN + " values): " +
                    Arrays.toString(Arrays.copyOf(medianProfile, printN)));
            System.out.println("  Debug: Median profile (last " + printN + " values): " +
                    Arrays.toString(Arrays.copyOfRange(medianProfile, (int) sizeY - printN, (int) sizeY)));
            System.out.println("  Debug: Detected Y pixel = " + detectedY + " (threshold = " + threshold + ")");
        }

        // Convert pixel Z to world coordinates
        AffineTransform3D sourceTransform = new AffineTransform3D();
        source.getSpimSource().getSourceTransform(timepoint, level, sourceTransform);

        // Transform the detected Z position to world coordinates
        double[] pixelPos = new double[]{0, detectedY, 0};
        double[] worldPos = new double[3];
        sourceTransform.apply(pixelPos, worldPos);

        return worldPos[2]; // Return world Z coordinate
    }

    /**
     * Computes the median of an array of values.
     */
    private float computeMedian(float[] values) {
        float[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n % 2 == 0) {
            return (float) ((sorted[n / 2 - 1] + sorted[n / 2]) / 2.0);
        } else {
            return sorted[n / 2];
        }
    }

    /**
     * Applies Z-drift compensation transforms to a source.
     */
    private void applyZDriftCompensation(SourceAndConverter<?> source, double[] driftValues) {
        int nTimepoints = driftValues.length;

        for (int t = 0; t < nTimepoints; t++) {
            if (Math.abs(driftValues[t]) > 1e-6) { // Only apply if there's significant drift
                AffineTransform3D compensation = new AffineTransform3D();
                compensation.translate(0, 0, -driftValues[t]); // Negative to compensate

                SourceAndConverterAndTimeRange<?> sourceAndTime =
                        new SourceAndConverterAndTimeRange<>(source, t);

                switch (mode) {
                    case "Mutate":
                        SourceTransformHelper.mutate(compensation, sourceAndTime);
                        break;
                    case "Append":
                        SourceTransformHelper.append(compensation, sourceAndTime);
                        break;
                }
            }
        }
    }
}
