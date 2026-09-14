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
package ch.epfl.biop.source;

import bdv.util.EmptyMultiresolutionSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.importer.EmptySourceCreator;

import java.util.function.Supplier;

public class EmptyMultiResolutionSourceCreator implements Runnable, Supplier<SourceAndConverter<?>> {

    AffineTransform3D at3D;

    long nx, ny, nz, nt;

    int scalex, scaley, scalez;

    int numberOfResolutions;

    String name;

    /**
     * Simple constructor
     * @param name name
     * @param at3D affine transform of the source
     * @param nx number of voxels in x
     * @param ny number of voxels in y
     * @param nz number of voxels in z
     * @param nt number of timepoints
     * @param scalex downscaling factor in x between resolution levels
     * @param scaley downscaling factor in y between resolution levels
     * @param scalez downscaling factor in z between resolution levels
     * @param numberOfResolutions number of resolution levels to generate
     */
    public EmptyMultiResolutionSourceCreator(
            String name,
            AffineTransform3D at3D,
            long nx, long ny, long nz, long nt,
            int scalex, int scaley, int scalez,
            int numberOfResolutions
    ) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.nt = nt;
        this.at3D = at3D;
        this.name = name;
        this.scalex = scalex;
        this.scaley = scaley;
        this.scalez = scalez;
        this.numberOfResolutions = numberOfResolutions;
    }

    @Override
    public void run() {

    }

    @Override
    public SourceAndConverter<?> get() {
        Source<UnsignedShortType> src = new EmptyMultiresolutionSource(nx,ny,nz,nt,at3D,name, scalex, scaley, scalez, numberOfResolutions);

        SourceAndConverter<?> source;

        source = SourceHelper.createSourceAndConverter(src);

        return source;
    }
}
