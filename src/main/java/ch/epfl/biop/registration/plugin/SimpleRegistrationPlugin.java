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
package ch.epfl.biop.registration.plugin;

import ij.ImagePlus;
import net.imglib2.realtransform.InvertibleRealTransform;

import java.util.Map;

@SuppressWarnings("SameReturnValue")
public interface SimpleRegistrationPlugin {

    /**
     * @return Sampling required for the registration, in micrometer
     */
    double getVoxelSizeInMicron();

    /**
     * Is called before registration to pass any extra registration parameter
     * argument. Passed as a dictionary of String to preserve serialization
     * capability.
     * @param parameters dictionary of parameters
     */
    void setRegistrationParameters(Map<String,String> parameters);

    /**
     *
     * @param fixed image
     * @param moving image
     * @param fixedMask optional mask for the fixed image (can be null)
     * @param movingMask optional mask for the moving image (can be null)
     * @return the transform, result of the registration, in
     * going from fixed to moving coordinates, in pixels
     */
    InvertibleRealTransform register(ImagePlus fixed, ImagePlus moving, ImagePlus fixedMask, ImagePlus movingMask);
}
