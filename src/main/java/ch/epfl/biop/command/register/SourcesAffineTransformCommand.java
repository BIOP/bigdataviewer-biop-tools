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
package ch.epfl.biop.command.register;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.SourceAndTimeRange;
import sc.fiji.bdvpg.source.transform.SourceTransformHelper;

import java.util.Arrays;

// Legacy command, kept for backward compatibility with existing scripts.
// Declared as a plain Command rather than a BdvPlaygroundActionCommand: it has no menu
// path, and the BDV Playground SourceService cannot register a menu-less action.
@Plugin(type = Command.class,
   //     menuPath = BdvPgMenus.RootMenu+"Source>Transform>Obsolete>Affine Transform Sources",
        description = "Applies an affine transformation to sources over a range of timepoints")
public class SourcesAffineTransformCommand implements BdvPlaygroundActionCommand {
    @Parameter(label = "Select Source(s)",
            description = "The sources to transform")
    SourceAndConverter[] sources_in;

    @Parameter(label = "Transform",
            description = "The affine transformation to apply")
    AffineTransform3D at3d;

    @Parameter(label = "Mode",
            choices = {"Mutate", "Append"},
            description = "Mutate modifies existing transform; Append adds a new transform layer")
    String mode = "Mutate";

    @Parameter(label = "Start Timepoint",
            description = "First timepoint to apply the transform to")
    int timepoint_begin;

    @Parameter(label = "End Timepoint",
            description = "Last timepoint to apply the transform to")
    int timepoint_end;

    @Override
    public void run() {

        Arrays.stream(sources_in).forEach(source -> {
            switch (mode) {
                case "Mutate":
                    SourceTransformHelper.mutate(at3d, new SourceAndTimeRange(source, timepoint_begin, timepoint_end));
                    break;
                case "Append":
                    SourceTransformHelper.append(at3d, new SourceAndTimeRange(source, timepoint_begin, timepoint_end));
                    break;
            }
        });

        SourceServices
                .getBdvDisplayService()
                .updateDisplays(sources_in);
    }
}
