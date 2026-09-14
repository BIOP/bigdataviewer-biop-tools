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
import ch.epfl.biop.dataset.reordered.LifReOrdered;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.service.SourceServices;

import java.util.List;


/**
 * NOT WORKING!! A Clone for affine transform or for the outofbounds stuff should be put somewhere
 * TO FIX (if the source is resampled, as in ABBA, that works however)
 *
 */
public class DemoShuffledSpimData {

    static {
        LegacyInjector.preinit();
    }

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) throws Exception {

        ij.ui().showUI();

        System.out.println(LifReOrdered.class.getSimpleName());
        BdvHandle bdv = SourceServices.getBdvDisplayService().getActiveBdv();

        System.out.println("Reordering dataset");
        LifReOrdered kd = new LifReOrdered("N:\\Temp Oli\\Kunal\\lifkunal-nico_v3.xml",16,4);
        kd.initialize();
        AbstractSpimData<?> reshuffled = kd.constructSpimData();

        System.out.println("Registering reordered dataset");
        SourceServices.getSourceService().register(reshuffled);

        System.out.println("Showing reordered dataset");

        final List<SourceAndConverter<?>> sourcesReordered = SourceServices
                .getSourceService()
                .getSourcesFromDataset(reshuffled);

        sourcesReordered.forEach( source -> {
            System.out.println(source.getSpimSource().getName());
            SourceServices.getBdvDisplayService().show( bdv, source );
        } );

        new ViewerTransformAdjuster( bdv, sourcesReordered.get(0) ).run();

        System.out.println("Showing reordered dataset - DONE");

    }

}
