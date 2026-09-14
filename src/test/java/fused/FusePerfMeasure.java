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

import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.command.importer.DatasetFromCZICreateCommand;
import ch.epfl.biop.command.exporter.BigStitcherDatasetToOMETIFFFuseCommand;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import org.apache.commons.io.FilenameUtils;

import java.io.File;

import static fiji.util.TicToc.tic;
import static fiji.util.TicToc.toc;

public class FusePerfMeasure {
    static ImageJ ij;

    static {
        LegacyInjector.preinit();
    }
    public static void main( String[] args ) throws Exception {

        /*Context ctx = new Context(CommandService.class,
                TaskService.class,
                SourceService.class,
                ConvertService.class
        );*/

        ij = new ImageJ();
        ij.ui().showUI();

        File cziTest = DatasetHelper.getDataset("https://zenodo.org/records/8303129/files/Demo%20LISH%204x8%2015pct%20647.czi");

        DebugTools.enableLogging ("OFF");

        // Get rid of xml dataset and bfmemo
        File xmlOut = new File (FilenameUtils.removeExtension(cziTest.getAbsolutePath())+".xml");
        System.out.println(xmlOut.getAbsolutePath());
        if (xmlOut.exists()) xmlOut.delete();
        File xmlOutBfMemo = new File (cziTest.getAbsolutePath()+".bfmemo");
        if (xmlOutBfMemo.exists()) xmlOutBfMemo.delete();
        System.out.println(xmlOutBfMemo);
        ij.command()
        //ctx.getService(CommandService.class)
                .run(DatasetFromCZICreateCommand.class, true,
                        "czi_file", cziTest,
                        "xml_out", xmlOut.getAbsolutePath(),
                        "erase_if_file_already_exists", true).get();

        tic();
        ij.command()
        //ctx.getService(CommandService.class)
                .run(BigStitcherDatasetToOMETIFFFuseCommand.class,
                true, "xml_bigstitcher_file", xmlOut,
                "output_path_directory", xmlOut.getParent(),
                "n_resolution_levels", 1,
                "split_slices", false,
                "split_channels", false,
                "split_frames", false,
                "override_z_ratio", false,
                "range_channels", "",
                "range_slices", "",
                "range_frames", "",
                "use_lzw_compression", false,
                "z_ratio", 1.0, // ignored
                "use_interpolation", false,
                "fusion_method", "SMOOTH AVERAGE"
        ).get();
        toc();
    }
}
