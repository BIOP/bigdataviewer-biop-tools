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
package irina;

import ch.epfl.biop.OMETiffMultiSeriesProcessorExporter;
import loci.common.DebugTools;
import net.imagej.ImageJ;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FixProjection {

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) {
        DebugTools.enableLogging("OFF");
        ij.ui().showUI();
        // 0 ---- Export to OME-TIFF and project tiles from a ND2 file
        // * input : nd2
        // * input : output folder
        // * output : files path (List<String> containing paths)
        /*String basePath = "E:/ir";
        String nd2Path = "N:/public/irina.khven_GR-LAMAN/transverse_e12_5_round4_embryo12_no_cleanup.nd2";//"D:/e12_5_saggital_round3_embryo4_middle008.nd2";*/



        OMETiffMultiSeriesProcessorExporter
                .builder(ij.context())
                .file(new File("N:/public/irina.khven_GR-LAMAN/1_A1.tif"))
                .outputFolder("E:/ir")
                .lzw()
                .projectMax()
                .nThreads(2) //Runtime.getRuntime().availableProcessors()-1)
                .nResolutionLevels(1)
                .downscaleFactorLevels(1)
                .removeZOffsets()
                /*.rangeS("0:9") // Only 2 series
                .rangeC("0")
                .rangeZ("2")*/
                .export();

        OMETiffMultiSeriesProcessorExporter
                .builder(ij.context())
                .file(new File("N:/public/irina.khven_GR-LAMAN/1_A1-Before.tif"))
                .outputFolder("E:/ir")
                .lzw()
                .projectMax()
                .nThreads(2) //Runtime.getRuntime().availableProcessors()-1)
                .nResolutionLevels(1)
                .downscaleFactorLevels(1)
                .removeZOffsets()
                /*.rangeS("0:9") // Only 2 series
                .rangeC("0")
                .rangeZ("2")*/
                .export();

        /*String landmarkFileUnwarp = "N:/public/irina.khven_GR-LAMAN/distortion/2022-04-12-Oil-landmarks.csv";
        int cropX = 50;
        int cropY = 20;*/

        /*List<String> projectedTilePaths =
                exportAndProjectTiles(basePath+ File.separator+"projected", nd2Path)
                        .values()
                        .stream()
                        .collect(Collectors.toList());*/



/*
        // 1 ---- Correct for distortion
        // * input : ome tiff file
        // * input : landmark file for correcting distortion
        // * input : cropx cropy in pixel
        // * output : files path (List<String> containing paths)

        List<String> undistortedTilePaths =  projectedTilePaths
                .stream()
                .parallel()
                .map(inputPath -> {
                    try {
                        return correctDistortion(basePath+File.separator+"undistorted", inputPath, landmarkFileUnwarp, cropX, cropY, false);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .collect(Collectors.toList());
        // 2 ---- Define dataset and prepare it for BigStitcher, keep pixel size somewhere
        // * input : undistorted files
        // * output : one xml file per connected tiles */

    }

    public static Map<String, String> exportAndProjectTiles(String exportPath, String filePath) {
        return OMETiffMultiSeriesProcessorExporter
                .builder(ij.context())
                .file(new File(filePath))
                .outputFolder(exportPath)
                .lzw()
                .projectMax()
                .nThreads(2) //Runtime.getRuntime().availableProcessors()-1)
                .nResolutionLevels(1)
                .downscaleFactorLevels(1)
                .removeZOffsets()
                /*.rangeS("0:9") // Only 2 series
                .rangeC("0")
                .rangeZ("2")*/
                .export();
    }
}
