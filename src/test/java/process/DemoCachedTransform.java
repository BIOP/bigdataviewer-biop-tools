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

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.util.RealTransformHelper;
import bdv.util.EmptySource;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.ARGBType;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.source.register.BigWarpLauncher;
import sc.fiji.bdvpg.source.transform.SourceRealTransformer;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DemoCachedTransform {

    static {
        LegacyInjector.preinit();
    }

    static public void main(String... args) {
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        demo3d();
    }


    public static void demo3d() {
        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        XMLToDatasetImporter importer = new XMLToDatasetImporter(filePath);

        AbstractSpimData<?> spimData = importer.get();

        SourceAndConverter<?> sourceFixed = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        importer = new XMLToDatasetImporter(filePath);

        spimData = importer.get();

        SourceAndConverter<?> sourceMoving = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);


        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceServices.getBdvDisplayService().getActiveBdv();

        // Show the sourceandconverter
        SourceServices.getBdvDisplayService().show(bdvHandle, sourceFixed);

        SourceServices.getSourceService().getConverterSetup(sourceMoving)
                .setColor(new ARGBType(ARGBType.rgba(0, 255, 255,0)));

        new BrightnessAutoAdjuster(sourceFixed, 0).run();

        new BrightnessAutoAdjuster(sourceMoving, 0).run();

        new ViewerTransformAdjuster(bdvHandle, sourceFixed).run();

        List<SourceAndConverter<?>> movingSources = new ArrayList<>();
        movingSources.add(sourceMoving);

        List<SourceAndConverter<?>> fixedSources = new ArrayList<>();
        fixedSources.add(sourceFixed);

        List<ConverterSetup> converterSetups = movingSources.stream().map(src -> SourceServices.getSourceService().getConverterSetup(src)).collect(Collectors.toList());
        converterSetups.addAll(fixedSources.stream().map(src -> SourceServices.getSourceService().getConverterSetup(src)).collect(Collectors.toList()));

        BigWarpLauncher bwl = new BigWarpLauncher(movingSources, fixedSources, "BigWarp Demo", converterSetups);
        bwl.run();

        bwl.getBigWarp().loadLandmarks( "src/test/resources/landmarks3d-demo.csv" );

        bwl.getBigWarp().toggleMovingImageDisplay();
        bwl.getBigWarp().matchActiveViewerPanelToOther();

        for (SourceAndConverter<?> source : bwl.getWarpedSources()) {
            SourceServices.getSourceService()
                    .register(source);
        }

        // Makes a source from a transform:

        //ITransformFieldSource source = new TransformFieldSource(bwl.getBigWarp().getBwTransform().getTransformation(0), "BigWarp Transformation");

        EmptySource.EmptySourceParams params = new EmptySource.EmptySourceParams();
        params.name = "Model Source";
        params.nx = 25; // <25 breaks!! singular matrix exception
        params.ny = 25;
        params.nz = 20; // could be anything ?
        params.at3D.scale(10,10,10);
        params.at3D.translate(-50,-50, -50);
        EmptySource model = new EmptySource(params);

        //Source<?> model = sourceFixed.getSpimSource();

        //ITransformFieldSource cached_transform = new ResampledTransformFromSourceFieldSource(source, model, "Cached transform");
        //RealTransform transform = new SourcedRealTransform(cached_transform);

        //BoundedRealTransform brt = new BoundedRealTransform(bwl.getBigWarp().getBwTransform().getTransformation(0), new FinalRealInterval(new double[]{20,20,20}, new double[]{150,150,150}));

        //ResampledTransformFieldSource tra

        RealTransform transform = RealTransformHelper.resampleTransform(bwl.getBigWarp().getBwTransform().getTransformation(0), model);

        SourceAndConverter tr = new SourceRealTransformer(null,transform).apply(sourceFixed);
        SourceServices.getBdvDisplayService()
                .show(bdvHandle, tr);

        SourceServices.getBdvDisplayService()
                .show(bdvHandle, bwl.getWarpedSources()[0]);

        /*SourceAndConverter resampled = new SourceResampler(fixedSources.get(0), SourceHelper.createSourceAndConverter(model), "Model Size", false, false, false, 0).get();

        SourceServices.getBdvDisplayService()
                .show(bdvHandle, resampled);*/

    }
}
