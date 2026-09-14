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
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

abstract public class SourcesForRegistrationSelectCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Fixed Source(s)",
            description = "Reference source(s) that remain stationary during registration")
    SourceAndConverter<?>[] sources_fixed;

    @Parameter(label = "Fixed Timepoint",
            description = "Timepoint of the fixed source to use for registration")
    int tp_fixed;

    @Parameter(label = "Fixed Resolution Level",
            description = "Resolution level of the fixed source (0 = highest resolution)")
    int level_fixed_source;

    @Parameter(label = "Moving Source(s)",
            description = "Source(s) to be aligned to the fixed reference")
    SourceAndConverter<?>[] sources_moving;

    @Parameter(label = "Moving Timepoint",
            description = "Timepoint of the moving source to use for registration")
    int tp_moving;

    @Parameter(label = "Moving Resolution Level",
            description = "Resolution level of the moving source (0 = highest resolution)")
    int level_moving_source;

    @Parameter(label = "Resampling Pixel Size",
            description = "Pixel size in world coordinates units used when resampling images for registration")
    double px_size_in_current_unit;

    @Parameter(label = "Interpolate",
            description = "When checked, uses interpolation when resampling images")
    boolean interpolate;

}
