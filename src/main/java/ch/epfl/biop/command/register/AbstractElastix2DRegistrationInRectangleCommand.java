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

import org.scijava.plugin.Parameter;

abstract class AbstractElastix2DRegistrationInRectangleCommand extends Abstract2DRegistrationInRectangleCommand {

    @Parameter(label = "Show Registration",
            description = "When checked, displays registration results in ImageJ windows (not compatible with RGB images)")
    boolean show_image_registration = false;

    @Parameter(label = "Iterations Per Scale",
            description = "Maximum number of optimization iterations at each pyramid level")
    int max_iteration_per_scale = 100;

    @Parameter(label = "Minimum Image Size",
            description = "Minimum image size in pixels for the coarsest pyramid level")
    int min_image_size_pix = 32;

    @Parameter(label = "Auto Center",
            description = "When checked, initially aligns images by their centers of gravity")
    boolean automatic_transform_initialization = false;

    @Parameter(label = "Verbose",
            description = "When checked, outputs detailed registration progress information")
    boolean verbose = false;



}
