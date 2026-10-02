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
import ch.epfl.biop.labkit.SourcesInputImage;
import org.scijava.Context;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.labkit.ui.LabkitFrame;

/**
 * Command to open the Labkit segmentation GUI for BigDataViewer sources.
 * <p>
 * This command wraps selected sources into a Labkit-compatible input image
 * and opens the Labkit pixel classification interface for interactive
 * segmentation and classifier training.
 * </p>
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu + "Process>Classify (Labkit)>Source - Open Labkit",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ProcessMenu, weight = BdvPgMenus.ProcessW),
                @Menu(label = "Classify (Labkit)", weight = -2.1),
                @Menu(label = "Source - Open Labkit", weight = 6)
        },
        description = "Opens the Labkit pixel classification GUI for the selected sources")
public class SourcesLabkitOpenCommand implements BdvPlaygroundActionCommand {

    @Parameter
    Context context;

    @Parameter(label = "Select Source(s)",
            style = "sorted",
            description = "The sources to open in Labkit (each source is treated as a channel)")
    SourceAndConverter<?>[] sources;

    @Parameter(label = "Resolution Level",
            min = "0",
            description = "Resolution level to use (0 = full resolution, higher = lower resolution)")
    int resolution_level = 0;

    @Override
    public void run() {
        if (sources == null || sources.length == 0) {
            System.err.println("No sources selected.");
            return;
        }

        // Create the InputImage wrapper for Labkit
        SourcesInputImage inputImage = new SourcesInputImage(sources, resolution_level);

        System.out.println("Opening Labkit with " + sources.length + " source(s):");
        for (int i = 0; i < sources.length; i++) {
            System.out.println("  Channel " + i + ": " + sources[i].getSpimSource().getName());
        }

        // Open Labkit with the input image
        LabkitFrame labkitFrame = LabkitFrame.showForImage(context, inputImage);

        // Add a listener to handle when Labkit is closed
        labkitFrame.onCloseListeners().addListener(() -> {
            System.out.println("Labkit window closed");
        });

        System.out.println("Labkit opened successfully!");
    }
}
