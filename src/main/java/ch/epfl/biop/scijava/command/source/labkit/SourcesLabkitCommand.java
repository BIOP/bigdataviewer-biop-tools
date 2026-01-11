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
import ch.epfl.biop.labkit.SourcesInputImage;
import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
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
        menuPath = ScijavaBdvDefaults.RootMenu + "Sources>Labkit>Open Labkit for sources",
        description = "Opens the Labkit pixel classification GUI for the selected sources")
public class SourcesLabkitCommand implements BdvPlaygroundActionCommand {

    @Parameter
    Context context;

    @Parameter(label = "Select Source(s)",
            style = "sorted",
            description = "The sources to open in Labkit (each source is treated as a channel)")
    SourceAndConverter<?>[] sacs;

    @Parameter(label = "Resolution Level",
            min = "0",
            description = "Resolution level to use (0 = full resolution, higher = lower resolution)")
    int resolution_level = 0;

    @Override
    public void run() {
        if (sacs == null || sacs.length == 0) {
            System.err.println("No sources selected.");
            return;
        }

        // Create the InputImage wrapper for Labkit
        SourcesInputImage inputImage = new SourcesInputImage(sacs, resolution_level);

        System.out.println("Opening Labkit with " + sacs.length + " source(s):");
        for (int i = 0; i < sacs.length; i++) {
            System.out.println("  Channel " + i + ": " + sacs[i].getSpimSource().getName());
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
