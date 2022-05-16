package distort;

import bdv.gui.TransformTypeSelectDialog;
import bdv.img.WarpedSource;
import bdv.util.BdvHandle;
import bdv.util.BigWarpHelper;
import bdv.viewer.SourceAndConverter;
import bigwarp.BigWarp;
import bigwarp.BigWarpInit;
import bigwarp.landmarks.LandmarkTableModel;
import bigwarp.transforms.BigWarpTransform;
import ch.epfl.biop.ImagePlusToOMETiff;
import ch.epfl.biop.OMETiffMultiSeriesProcessorExporter;
import ch.epfl.biop.kheops.KheopsHelper;
import ch.epfl.biop.sourceandconverter.EmptyMultiResolutionSourceAndConverterCreator;
import ch.epfl.biop.sourceandconverter.exporter.CZTRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
import ch.epfl.biop.wrappers.transformix.TransformHelper;
import ij.ImagePlus;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import org.scijava.Context;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
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
        String basePath = "E:/irinatest";
        String nd2Path = "N:/public/irina.khven_GR-LAMAN/e12_5_saggital_round3_embryo4_middle008.nd2";
        List<String> projectedTilePaths =
                exportAndProjectTiles(basePath+File.separator+"projected", nd2Path)
                        .values()
                        .stream()
                        .collect(Collectors.toList());

        projectedTilePaths.forEach(System.out::println);

        // 1 ---- Correct for distortion
        // * input : ome tiff file
        // * input : landmark file for correcting distortion
        // * input : cropx cropy in pixel
        // * output : files path (List<String> containing paths)
        String landmarkFileUnwarp = "N:/public/irina.khven_GR-LAMAN/distortion/2022-04-12-Oil-landmarks.csv";
        int cropX = 50;
        int cropY = 20;
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
                .nResolutionLevels(1)
                .downscaleFactorLevels(1)
                .removeZOffsets()
                .rangeS("10:14") // Only 2 series
                .rangeC("0,1")
                .export();
    }

    public static String correctDistortion( String exportPath, String filePath, String landmarkFileUnwarp, int cropX, int cropY, boolean interpolate) throws Exception {

        List<SourceAndConverter> sources = KheopsHelper.getSourcesFromFile(filePath, 1024, 1024, 16, 1).idToSources.get(0);

        // Needs to uncalibrate before warping
        AffineTransform3D transform = new AffineTransform3D();
        sources.get(0).getSpimSource().getSourceTransform(0,0,transform);

        Function<SourceAndConverter,SourceAndConverter>
                physicalToPixel = source ->
                SourceTransformHelper.createNewTransformedSourceAndConverter(transform.inverse(), new SourceAndConverterAndTimeRange(source,0));

        SourceAndConverter uncroppedModel = physicalToPixel.apply(sources.get(0));

        long nPx = uncroppedModel.getSpimSource().getSource(0,0).max(0)-2*cropX;
        long nPy = uncroppedModel.getSpimSource().getSource(0,0).max(1)-2*cropY;
        long nPz = uncroppedModel.getSpimSource().getSource(0,0).max(2)+1; // Humpf
        AffineTransform3D location = new AffineTransform3D();
        location.translate(cropX, cropY, 0);
        SourceAndConverter croppedModel = new EmptySourceAndConverterCreator("model", location, nPx, nPy, nPz).get();

        RealTransform realTransform = BigWarpHelper.realTransformFromBigWarpFile(new File(landmarkFileUnwarp), true);

        Function<SourceAndConverter,SourceAndConverter> unwarp = new SourceRealTransformer(null, realTransform);

        Function<SourceAndConverter,SourceAndConverter> cropAndRaster = new SourceResampler(null,croppedModel,"Model_Cropped", false, false, interpolate, 0);

        Function<SourceAndConverter,SourceAndConverter>
                pixelToPhysical = source ->
                SourceTransformHelper.createNewTransformedSourceAndConverter(transform, new SourceAndConverterAndTimeRange(source,0));

        List<SourceAndConverter> correctedSources = sources.stream()
                .map(physicalToPixel)
                .map(unwarp)
                .map(cropAndRaster)
                .map(pixelToPhysical)
                .collect(Collectors.toList());

        CZTRange range = new CZTRange.Builder().get(sources.size(),1,1);

        ImagePlus undistorted = ImagePlusGetter.getImagePlus(new File(filePath).getName(), correctedSources, 0, range, true, false, false, null);

        String totalPath = exportPath+File.separator+undistorted.getTitle();

        ImagePlusToOMETiff.writeToOMETiff(undistorted, new File(totalPath), 4, 2, "LZW");

        return totalPath;
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
