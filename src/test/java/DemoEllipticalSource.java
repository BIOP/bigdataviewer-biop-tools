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
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.command.workflow.elliptic.Elliptic3DTransformCreatorCommand;
import ch.epfl.biop.command.register.SourcesRealTransformCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.realtransform.RealTransform;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAdjuster;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

import java.util.concurrent.ExecutionException;

public class DemoEllipticalSource {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        XMLToDatasetImporter importer = new XMLToDatasetImporter(filePath);
        final AbstractSpimData spimData = importer.get();

        SourceAndConverter source = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        new BrightnessAdjuster(source,0,250).run();

        try {
            RealTransform rt = (RealTransform) ij.command().run(Elliptic3DTransformCreatorCommand.class, true,
                    "radius_x",100,
                    "radius_y",100,
                    "radius_z",100, // radii of axes 1 2 3 of ellipse
                    "rotation_x",0,
                    "rotation_y",0,
                    "rotation_z",0, // 3D rotation euler angles  - maybe not the best parametrization
                    "center_x",120,
                    "center_y",120,
                    "center_z",120).get().getOutput("e3dt");
            SourceAndConverter transformed_source = ((SourceAndConverter[]) ij.command().run(SourcesRealTransformCommand.class, true,
                    "sources_in", new SourceAndConverter[]{source},
                    "rt", rt).get().getOutput("sources_out"))[0];
            BdvHandle bdvh = SourceServices
                    .getBdvDisplayService()
                    .getNewBdv();
            SourceServices
                    .getBdvDisplayService()
                    .show(bdvh, transformed_source);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

    }

}
