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
