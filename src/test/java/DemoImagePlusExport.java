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
import ch.epfl.biop.command.display.bdv.export.BdvViewToImagePlusBasicExportCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;

public class DemoImagePlusExport
{
    static final ImageJ ij = new ImageJ();

    static {
        LegacyInjector.preinit();
    }

    static public void main(String... args) {
        ij.ui().showUI();

        demo();
    }


    public static void demo() {
        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        XMLToDatasetImporter importer = new XMLToDatasetImporter(filePath);

        AbstractSpimData<?> spimData = importer.get();

        SourceAndConverter<?> source = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        // Creates a BdvHandle
        BdvHandle bdvHandle = SourceServices.getBdvDisplayService().getActiveBdv();

        // Show the sourceandconverter
        SourceServices.getBdvDisplayService().show(bdvHandle, source);
        new BrightnessAutoAdjuster(source, 0).run();
        new ViewerTransformAdjuster(bdvHandle, source).run();

        // Export
        ij.context()
                .getService( CommandService.class)
                .run( BdvViewToImagePlusBasicExportCommand.class, true,
                        "bdv_h", bdvHandle,
                        "capturename", "image",
                        "zsize", 20,
                        "samplingxyinphysicalunit", 1,
                        "samplingzinphysicalunit", 1,
                        "interpolate", true,
                        "unit", "px",
                        "selected_timepoints_str", "",
                        "export_mode", "Normal",
                        "range", ""
                );
    }
}
