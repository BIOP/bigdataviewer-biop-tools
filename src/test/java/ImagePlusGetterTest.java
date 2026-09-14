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
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.exporter.CZTRange;
import ch.epfl.biop.source.exporter.ImagePlusGetter;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

import java.util.ArrayList;
import java.util.List;

public class ImagePlusGetterTest {


    static {
        LegacyInjector.preinit();
    }

    public static <T extends NumericType<T> & NativeType<T>> void main(String... args) throws Exception {

        ImageJ ij = new ImageJ();
        ij.ui().showUI();
        final String filePath = "src/test/resources/mri-stack.xml";

        DebugTools.enableIJLogging(true);

        //final String filePath = "src/test/resources/mitosis.xml";
        DebugTools.enableIJLogging(true);
        DebugTools.enableLogging("DEBUG");

        //final String filePath = "D:/Operetta Dataset/Opertta Tiling Magda/MagdaData.xml";
        //final String filePath = "N:/temp-romain/TL2_bdv.xml";
        // Import SpimData
        XMLToDatasetImporter importer = new XMLToDatasetImporter(filePath);
        //importer.run();

        final AbstractSpimData spimData = importer.get();

        List<SourceAndConverter<?>> allSources = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData);

        // Creates a BdvHandle
        //BdvHandle bdvHandle = SourceServices
        //        .getSourceAndConverterDisplayService().getActiveBdv();
        /*SourceServices
                .getSourceAndConverterDisplayService()
                .show(source);*/
        ArrayList<SourceAndConverter<?>> sources = new ArrayList<>();
        sources.add(allSources.get(0));

        if (allSources.size()>1) {
            sources.add(allSources.get(1));
        }


        //ImagePlusGetter.getImagePlus("TestMri", rai).show();
        CZTRange range = ImagePlusGetter
                .fromSources(sources,0,0);

        TaskService taskService = ij.get(TaskService.class);

        Task nonVirtual = taskService.createTask("nonVirtual");
        List<SourceAndConverter<T>> sanitizedList = ImagePlusGetter.sanitizeList(sources);
        ImagePlusGetter.getImagePlus("Non Virtual",
                sanitizedList,
                0,
                range,
                true,
                false,
                true,
                nonVirtual).show();
        Task virtual = taskService.createTask("virtual");
        ImagePlusGetter.getVirtualImagePlus("Virtual", sanitizedList, 0, range, true, virtual).show();//ImagePlusGetter.getVirtualImagePlus("Virtual no cache", sources, 0, range, false, true).show();
        ImagePlusGetter.getVirtualImagePlus("Virtual no cache", sanitizedList, 0, range, false, null).show();//ImagePlusGetter.getVirtualImagePlus("Virtual no cache", sources, 0, range, false, true).show();

    }
}
