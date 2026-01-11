/*-
 * #%L
 * Labkit Integration for BigDataViewer-Playground - BIOP - EPFL
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
package ch.epfl.biop.scijava.command.source.labkit;

import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceLabkitClassifier;

import java.io.File;

/**
 * Command to create a lazy Labkit-classified source from BigDataViewer sources.
 * <p>
 * This command applies a pre-trained Labkit classifier to the selected sources
 * and creates a new source containing the segmentation result. The segmentation
 * is computed lazily (on-demand) as tiles are requested.
 * </p>
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu + "Sources>Labkit>Apply Labkit classifier to sources",
        description = "Creates a lazy segmentation source by applying a Labkit classifier to selected sources")
public class SourcesLabkitClassifierCommand implements BdvPlaygroundActionCommand {

    @Parameter
    Context context;

    @Parameter(label = "Select Source(s)",
            style = "sorted",
            description = "The sources to classify (each source is treated as a channel)")
    SourceAndConverter<?>[] sacs;

    @Parameter(label = "Classifier File",
            style = "open",
            description = "Path to the Labkit .classifier file")
    File classifier_file;

    @Parameter(label = "Resolution Level",
            min = "0",
            description = "Resolution level to use from input sources (0 = full resolution)")
    int resolution_level = 0;

    @Parameter(label = "Output Name Suffix",
            description = "Suffix appended to the source name for the classified output")
    String suffix = "_classified";

    @Parameter(label = "Use GPU",
            description = "Use GPU acceleration for classification (requires compatible GPU and OpenCL)")
    boolean use_gpu = false;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The classified source")
    SourceAndConverter<UnsignedByteType> sac_out;

    @Override
    public void run() {
        if (sacs == null || sacs.length == 0) {
            System.err.println("No sources selected.");
            return;
        }

        if (classifier_file == null || !classifier_file.exists()) {
            System.err.println("Classifier file not found: " + classifier_file);
            return;
        }

        String classifierPath = classifier_file.getAbsolutePath();
        String outputName = sacs[0].getSpimSource().getName() + suffix;

        System.out.println("Applying Labkit classifier to " + sacs.length + " source(s):");
        for (int i = 0; i < sacs.length; i++) {
            System.out.println("  Channel " + i + ": " + sacs[i].getSpimSource().getName());
        }
        System.out.println("Classifier: " + classifierPath);
        System.out.println("Resolution level: " + resolution_level);
        System.out.println("GPU acceleration: " + (use_gpu ? "enabled" : "disabled"));

        // Create the classified source using SourceLabkitClassifier
        SourceLabkitClassifier classifier = new SourceLabkitClassifier(
                sacs,
                classifierPath,
                context,
                outputName,
                resolution_level,
                use_gpu
        );

        sac_out = classifier.get();

        System.out.println("Created classified source: " + sac_out.getSpimSource().getName());
    }
}
