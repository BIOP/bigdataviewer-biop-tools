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
package process;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.SourceHelper;
import ch.epfl.biop.source.SourceVoxelProcessor;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import sc.fiji.bdvpg.command.process.SourceWithLUTDuplicateCommand;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.importer.VoronoiSourceCreator;

public class DemoCachedBorders {
    public static void main(String... args) throws Exception {
        final net.imagej.ImageJ ij = new ImageJ();
         // This has been transfered to DEMO, except for lazy pyramidize
        DebugTools.enableLogging ("OFF");
        ij.ui().showUI();

        SourceAndConverter<FloatType> voronoi = new VoronoiSourceCreator(new long[]{4096*2, 4096*2, 4096*2}, 100000, false).get();

        BdvHandle bdvh = SourceServices.getBdvDisplayService().getNewBdv();

        SourceAndConverter<UnsignedByteType> borders = SourceVoxelProcessor.getBorders(voronoi);

        SourceAndConverter<?>[] reColoredVoronoi = (SourceAndConverter<?>[])
                ij.module().run(ij.command().getCommand(SourceWithLUTDuplicateCommand.class), true,
                        "sources", new SourceAndConverter[]{voronoi}
                ).get().getOutput("sources_out");

        SourceServices.getBdvDisplayService().show(bdvh, reColoredVoronoi[0]);
        SourceServices
                .getSourceService()
                        .getConverterSetup(reColoredVoronoi[0]).setDisplayRange(0,1280);

        SourceServices
                .getSourceService()
                .getConverterSetup(borders).setDisplayRange(0,256);

        SourceServices.getBdvDisplayService().show(bdvh, borders);

        SourceAndConverter<UnsignedByteType> smoothenedBorders = SourceHelper.lazyPyramidizeXY2(borders);

        SourceServices
                .getSourceService()
                .getConverterSetup(smoothenedBorders).setDisplayRange(0,256);


        SourceServices
                .getSourceService()
                .getConverterSetup(smoothenedBorders).setColor(new ARGBType(ARGBType.rgba(128, 255, 120, 60)));

        SourceServices.getBdvDisplayService().show(bdvh, smoothenedBorders);

    }
}
