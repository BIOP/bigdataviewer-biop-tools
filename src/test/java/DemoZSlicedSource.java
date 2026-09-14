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
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.slicer.SlicerViews;
import bdv.viewer.SourceAndConverter;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

import java.util.List;


/**
 * Apparently this works now.
 */
public class DemoZSlicedSource {

    static {
        LegacyInjector.preinit();
    }

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {

        ij.ui().showUI();

        // load and convert the famous blobs image// Gets active BdvHandle instance
        BdvHandle bdv = SourceServices.getBdvDisplayService().getActiveBdv();
        // Import SpimData
        new XMLToDatasetImporter("src/test/resources/mri-stack.xml").run();

        final List<SourceAndConverter<?>> sources = SourceServices.getSourceService().getSources();

        sources.forEach( source -> {
            SourceServices.getBdvDisplayService().show( bdv, source );
            new ViewerTransformAdjuster( bdv, source ).run();
            new BrightnessAutoAdjuster<>( source, 0 ).run();
        } );

        RandomAccessibleInterval<?> nonResliced = sources.get(0).getSpimSource().getSource(0,0);

        ExtendedRandomAccessibleInterval rai = SlicerViews.extendSlicer(nonResliced,2,0);

        BdvFunctions.show(
        Views.interval(rai,
                new FinalInterval(nonResliced.dimension(0)*nonResliced.dimension(2),
                        nonResliced.dimension(1),
                        1)
                       ),
                "Sliced"
                );

    }

}
