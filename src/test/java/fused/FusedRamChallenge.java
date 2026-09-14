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
package fused;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.EmptyMultiResolutionSourceCreator;
import ij.IJ;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

import javax.swing.tree.TreePath;

public class FusedRamChallenge {

    static ImageJ ij;

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        DebugTools.enableLogging ("OFF");
        ij = new ImageJ();
        ij.ui().showUI();

        double oX = 0;
        double oY = 0;
        double oZ = 0;

        double sx = 24.0;
        double sy = 16;
        double sz = 0.01;

        int nPx = 3000*24;
        int nPy = (int) (((double)nPx)*sy/sx);
        int nPz = 1;

        AffineTransform3D transform = new AffineTransform3D();
        transform.scale(sx/nPx,sy/nPy,sz/nPz);
        transform.translate(oX,oY,oZ);

        SourceAndConverter model = new EmptyMultiResolutionSourceCreator("model", transform, nPx, nPy, nPz, 1, 2, 2, 2, 1).get();

        SourceServices.getSourceService().register(model);

        IJ.log(" Loading dataset ");
        AbstractSpimData asd = new XMLToDatasetImporter("C:\\Users\\nicol\\Downloads\\CompositeTiles\\CompositeTiles\\tiles.xml").get();

        IJ.log(" Dataset loaded ");

        String sourcesPath = "tiles.xml>Channel>0";

        SourceService source_service = ij.get(SourceService.class);

        TreePath tp =
                source_service
                .tree()
                .getTreePathFromString(sourcesPath);

        //List<SourceAndConverter> sources = source_service.getUI().getSourceAndConvertersFromTreePath(tp);

        //IJ.log("Now fusing "+sources.size()+" sources");

        //SourceAndConverter fused = new SourceFuserAndResampler(sources, AlphaFusedResampledSource.AVERAGE, model, "Fused_ch0", true, true, false, 0, 1024,1024,1,10).get();

        //source_service.register(fused);
    }
}
