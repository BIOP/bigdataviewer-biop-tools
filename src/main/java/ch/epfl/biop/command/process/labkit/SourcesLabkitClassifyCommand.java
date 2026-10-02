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
package ch.epfl.biop.command.process.labkit;

import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import ch.epfl.biop.labkit.SourceLabkitClassifier;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

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
        //menuPath = BdvPgMenus.RootMenu + "Process>Classify (Labkit)>Source - Apply Labkit Classifier",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ProcessMenu, weight = BdvPgMenus.ProcessW),
                @Menu(label = "Classify (Labkit)", weight = -2.1),
                @Menu(label = "Source - Apply Labkit Classifier", weight = 6)
        },
        description = "Creates a lazy segmentation source by applying a Labkit classifier to selected sources")
public class SourcesLabkitClassifyCommand implements BdvPlaygroundActionCommand {

    @Parameter
    Context context;

    @Parameter(label = "Select Source(s)",
            style = "sorted",
            description = "The sources to classify (each source is treated as a channel)")
    SourceAndConverter<?>[] sources;

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
    SourceAndConverter<UnsignedByteType> source_out;

    @Override
    public void run() {
        if (sources == null || sources.length == 0) {
            System.err.println("No sources selected.");
            return;
        }

        if (classifier_file == null || !classifier_file.exists()) {
            System.err.println("Classifier file not found: " + classifier_file);
            return;
        }

        String classifierPath = classifier_file.getAbsolutePath();
        String outputName = sources[0].getSpimSource().getName() + suffix;

        System.out.println("Applying Labkit classifier to " + sources.length + " source(s):");
        for (int i = 0; i < sources.length; i++) {
            System.out.println("  Channel " + i + ": " + sources[i].getSpimSource().getName());
        }
        System.out.println("Classifier: " + classifierPath);
        System.out.println("Resolution level: " + resolution_level);
        System.out.println("GPU acceleration: " + (use_gpu ? "enabled" : "disabled"));

        // Create the classified source using SourceLabkitClassifier
        SourceLabkitClassifier classifier = new SourceLabkitClassifier(
                sources,
                classifierPath,
                context,
                outputName,
                resolution_level,
                use_gpu
        );

        source_out = classifier.get();

        System.out.println("Created classified source: " + source_out.getSpimSource().getName());
    }
}
