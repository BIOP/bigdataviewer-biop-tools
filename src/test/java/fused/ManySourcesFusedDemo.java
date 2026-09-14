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

import bdv.util.BdvHandle;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import ch.epfl.biop.source.SourceFuserAndResampler;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.source.importer.EmptySourceCreator;
import sc.fiji.bdvpg.source.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

import java.util.ArrayList;
import java.util.List;

public class ManySourcesFusedDemo {

    static ImageJ ij;

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ij = new ImageJ();
        ij.ui().showUI();

        DebugTools.setRootLevel("OFF");
        demo(50);

        AffineTransform3D location = new AffineTransform3D();
        location.scale(0.5);
        location.translate(0,0,120);

        SourceAndConverter model = new EmptySourceCreator("Model", location, 8000,6000,1).get();

        List<SourceAndConverter<?>> all_sources = SourceServices.getSourceService().getSources();

        SourceServices.getSourceService().register(model);

        SourceAndConverter<?> fused = new SourceFuserAndResampler(all_sources,
                AlphaFusedResampledSource.AVERAGE,
                model, "Fused source",
                true, true, false, 0,
                64, 64, 64, 1000,8).get();

        BdvHandle bdvh = SourceServices.getBdvDisplayService().getNewBdv();

        SourceServices
                .getBdvDisplayService().show(bdvh, fused);

        new ViewerTransformAdjuster(bdvh, fused).run();

    }

    @Test
    public void demoRunOk() {
        main(new String[]{""});
    }

    @After
    public void closeFiji() {
        TestHelper.closeFijiAndBdvs(ij);
    }

    public static void demo(int numberOfSourcesInOneAxis) {

        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceServices
                .getBdvDisplayService().getActiveBdv();

        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        XMLToDatasetImporter importer = new XMLToDatasetImporter(filePath);

        final AbstractSpimData<?> spimData = importer.get();

        SourceAndConverter<?> source = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        new ViewerTransformAdjuster(bdvHandle, source).run();
        new BrightnessAutoAdjuster<>(source, 0).run();

        ArrayList<SourceAndConverter<?>> sources = new ArrayList<>();
        for (int x = 0; x < numberOfSourcesInOneAxis;x++) {
            for (int y = 0; y < numberOfSourcesInOneAxis; y++) {

                if (Math.random()>0.0) {
                    AffineTransform3D at3d = new AffineTransform3D();

                    at3d.rotate(2, Math.random());
                    at3d.scale(0.5 + Math.random() / 4, 0.5 + Math.random() / 4, 1);
                    at3d.translate(200 * x, 200 * y, 0);

                    SourceAndConverter<?> transformedSac = new SourceAffineTransformer<>(source, at3d).get();

                    sources.add(transformedSac);
                }
            }
        }

        SourceServices
                .getBdvDisplayService()
                .show(bdvHandle, sources.toArray(new SourceAndConverter[0]));

        SourceGroup sg = bdvHandle.getViewerPanel().state().getGroups().get(1);

        bdvHandle.getViewerPanel().state().addSourcesToGroup(sources, sg);
    }
}
