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

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;

abstract class Abstract2DRegistrationInRectangleCommand extends SourcesForRegistrationSelectCommand {

    @Parameter(label = "ROI Position X",
            style = "format:0.#####E0",
            description = "X coordinate of the registration region top-left corner")
    double px;

    @Parameter(label = "ROI Position Y",
            style = "format:0.#####E0",
            description = "Y coordinate of the registration region top-left corner")
    double py;

    @Parameter(label = "ROI Position Z",
            style = "format:0.#####E0",
            description = "Z coordinate of the registration plane")
    double pz;

    @Parameter(label = "ROI Size X",
            style = "format:0.#####E0",
            description = "Width of the registration region")
    double sx;

    @Parameter(label = "ROI Size Y",
            style = "format:0.#####E0",
            description = "Height of the registration region")
    double sy;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The computed affine transformation to align moving to fixed")
    AffineTransform3D at3d;

}
