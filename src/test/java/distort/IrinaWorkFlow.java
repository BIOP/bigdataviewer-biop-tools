package distort;

import ch.epfl.biop.OMETiffMultiSeriesProcessorExporter;
import loci.common.DebugTools;
import net.imagej.ImageJ;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IrinaWorkFlow {

    static final ImageJ ij = new ImageJ();

    static public void main(String... args) {
        DebugTools.enableLogging("OFF");
        ij.ui().showUI();
        // 0 ---- Export to OME-TIFF and project tiles from a ND2 file
        // * input : nd2
        // * input : output folder
        // * output : files path (List<String> containing paths)
        String basePath = "dir";
        List<String> projectedTilePaths =
                exportAndProjectTiles(basePath+File.separator+"projected", "nd2 file path")
                        .values()
                        .stream()
                        .collect(Collectors.toList());

        projectedTilePaths.parallelStream().forEach(System.out::println);
        // 1 ---- Correct for distortion
        // * input : ome tiff file
        // * input : landmark file for correcting distortion
        // * input : cropx cropy in pixel
        // * output : files path (List<String> containing paths)
        /*String landmarkFileUnwarp = "landmarkpath";
        int cropX = 50;
        int cropY = 20;
        List<String> undistortedTilePaths =  projectedTilePaths
                .stream()
                //.parallel()
                .map(inputPath -> correctDistortion(basePath+File.separator+"undistorted", inputPath, landmarkFileUnwarp, cropX, cropY))
                .collect(Collectors.toList());
        // 2 ---- Define dataset and prepare it for BigStitcher, keep pixel size somewhere
        // * input : undistorted files
        String xmlFile = getXmlDataset(undistortedTilePaths);
        String xmlBigStitcherFile = getBigStitcherXmlDataset(xmlFile);
        // TODO : select a subset of images.
        String xmlBigStitcherStitchedFile = stitchDataset(xmlBigStitcherFile);
        String omeTiffFusedPath = fuseDataset(xmlBigStitcherStitchedFile);
        // Optional : flip / rotate*/
    }

    public static Map<String, String> exportAndProjectTiles(String exportPath, String filePath) {
        return OMETiffMultiSeriesProcessorExporter
                .builder(ij.context())
                .file(new File(filePath))
                .outputFolder(exportPath)
                .lzw()
                .projectMax()
                .nThreads(Runtime.getRuntime().availableProcessors()-1)
                .nResolutionLevels(4)
                .downscaleFactorLevels(2)
                .removeZOffsets()
                .export();
    }

    public static String correctDistortion(String exportPath, String filePath, String landmarkFileUnwarp, int cropX, int cropY) {
        return null;
    }

    public static String getXmlDataset(List<String> undistortedTilePaths) {
        return null;
    }

    public static String getBigStitcherXmlDataset(String xmlDataset) {
        return null;
    }

    public static String stitchDataset(String xmlDataset) {
        return null;
    }

    public static String fuseDataset(String xmlDataset) {
        return null;
    }


}
