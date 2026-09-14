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
package ch.epfl.biop.command.process.transform;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.SourceAndTimeRange;
import sc.fiji.bdvpg.source.transform.SourceTransformHelper;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = BdvPgMenus.RootMenu+"Process>Transform>Source - Recenter Sources",
        description = "Moves sources so their center is at the specified coordinates")
public class SourcesRecenterCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Timepoint",
            description = "Timepoint used for computing the recentering transform")
    int timepoint = 0;

    @Parameter(label = "Center X",
            style = "format:0.#####E0",
            description = "Target X coordinate for the source center")
    double cx;

    @Parameter(label = "Center Y",
            style = "format:0.#####E0",
            description = "Target Y coordinate for the source center")
    double cy;

    @Parameter(label = "Center Z",
            style = "format:0.#####E0",
            description = "Target Z coordinate for the source center")
    double cz;

    @Parameter(label = "Select Source(s)",
            type = ItemIO.BOTH,
            description = "The sources to recenter")
    public SourceAndConverter<?>[] sources;

    @Parameter(label = "Mode",
            choices = {"Mutate", "Append"},
            description = "Mutate modifies existing transform; Append adds a new transform layer")
    String mode = "Mutate";

    @Override
    public void run() {
        for (SourceAndConverter<?> source: sources) {

            long sx = source.getSpimSource().getSource(timepoint, 0).dimension(0);

            long sy = source.getSpimSource().getSource(timepoint, 0).dimension(1);

            AffineTransform3D at3D = new AffineTransform3D();

            source.getSpimSource().getSourceTransform(timepoint, 0, at3D);

            AffineTransform3D at3DCenter = new AffineTransform3D();
            at3DCenter.concatenate(at3D.inverse());
            at3DCenter.translate((double) -sx /2.0, (double) -sy /2.0,0);
            at3D.set(cx,0,3);
            at3D.set(cy,1,3);
            at3D.set(cz,2,3);
            at3DCenter.preConcatenate(at3D);

            switch (mode) {
                case "Mutate":
                    SourceTransformHelper.mutate(at3DCenter, new SourceAndTimeRange<>(source, timepoint));
                    break;
                case "Append":
                    SourceTransformHelper.append(at3DCenter, new SourceAndTimeRange<>(source, timepoint));
                    break;
            }
        }

        SourceServices
                .getBdvDisplayService()
                .updateDisplays(sources);

    }
}
