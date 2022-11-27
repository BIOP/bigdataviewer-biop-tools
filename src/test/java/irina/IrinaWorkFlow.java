package irina;

import bdv.util.BigWarpHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.ImagePlusToOMETiff;
import ch.epfl.biop.OMETiffMultiSeriesProcessorExporter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.bdv.img.bioformats.entity.FileName;
import ch.epfl.biop.bdv.img.legacy.bioformats.command.BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand;
import ch.epfl.biop.bdv.img.legacy.bioformats.entity.FileIndex;
import ch.epfl.biop.kheops.KheopsHelper;
import ch.epfl.biop.scijava.command.spimdata.DatasetToBigStitcherDatasetCommand;
import ch.epfl.biop.sourceandconverter.exporter.CZTRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
import ij.ImagePlus;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import org.apache.commons.io.FilenameUtils;
import org.scijava.Context;
import org.scijava.command.CommandService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterServiceUI;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;
import sc.fiji.bdvpg.spimdata.exporter.XmlFromSpimDataExporter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
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
        String basePath = "E:/ir";
        String nd2Path = "N:/public/irina.khven_GR-LAMAN/transverse_e12_5_round4_embryo12_no_cleanup.nd2";//"D:/e12_5_saggital_round3_embryo4_middle008.nd2";
        String landmarkFileUnwarp = "N:/public/irina.khven_GR-LAMAN/distortion/2022-04-12-Oil-landmarks.csv";
        int cropX = 50;
        int cropY = 20;

        List<String> projectedTilePaths =
                exportAndProjectTiles(basePath+File.separator+"projected", nd2Path)
                        .values()
                        .stream()
                        .collect(Collectors.toList());

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

        List<String> xmlFiles = getConnectedXmlDatasets(ij.context(), undistortedTilePaths);

        for (String xmlFile:xmlFiles) {
            String xmlBigStitcherFile = getBigStitcherXmlDataset(ij.context(), xmlFile);
            /*String xmlBigStitcherStitchedFile = stitchDataset(xmlBigStitcherFile);
            String omeTiffFusedPath = fuseDataset(xmlBigStitcherStitchedFile);*/
        }
        // Optional : flip / rotate*/
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

    public static <T extends NumericType<T> & NativeType<T>> String correctDistortion(String exportPath, String filePath, String landmarkFileUnwarp, int cropX, int cropY, boolean interpolate) throws Exception {

        List<SourceAndConverter> sources = KheopsHelper.getSourcesFromFile(filePath, 1024, 1024, 16, 1).idToSources.get(0);

        // Needs to uncalibrate before warping
        AffineTransform3D transform = new AffineTransform3D();
        sources.get(0).getSpimSource().getSourceTransform(0,0,transform);

        Function<SourceAndConverter<?>,SourceAndConverter<?>>
                physicalToPixel = source ->
                SourceTransformHelper.createNewTransformedSourceAndConverter(transform.inverse(), new SourceAndConverterAndTimeRange(source,0));

        SourceAndConverter uncroppedModel = physicalToPixel.apply(sources.get(0));

        long nPx = uncroppedModel.getSpimSource().getSource(0,0).max(0)- 2L *cropX;
        long nPy = uncroppedModel.getSpimSource().getSource(0,0).max(1)- 2L *cropY;
        long nPz = uncroppedModel.getSpimSource().getSource(0,0).max(2)+1; // Humpf
        AffineTransform3D location = new AffineTransform3D();
        location.translate(cropX, cropY, 0);
        SourceAndConverter croppedModel = new EmptySourceAndConverterCreator("model", location, nPx, nPy, nPz).get();

        RealTransform realTransform = BigWarpHelper.realTransformFromBigWarpFile(new File(landmarkFileUnwarp), true);

        Function<SourceAndConverter<?>,SourceAndConverter<?>> unwarp = new SourceRealTransformer(null, realTransform);

        Function<SourceAndConverter<?>,SourceAndConverter<?>> cropAndRaster = new SourceResampler(null,croppedModel,"Model_Cropped", false, false, interpolate, 0);

        Function<SourceAndConverter<?>,SourceAndConverter<?>>
                pixelToPhysical = source ->
                SourceTransformHelper.createNewTransformedSourceAndConverter(transform, new SourceAndConverterAndTimeRange(source,0));

        List<SourceAndConverter<?>> correctedSources = sources.stream()
                .map(src -> (SourceAndConverter<?>) src)
                .map(physicalToPixel)
                .map(unwarp)
                .map(cropAndRaster)
                .map(pixelToPhysical)
                .collect(Collectors.toList());

        CZTRange range = new CZTRange.Builder().get(sources.size(),1,1);

        List<SourceAndConverter<T>> sanitizedList = ImagePlusGetter.sanitizeList(correctedSources);

        ImagePlus undistorted = ImagePlusGetter.getImagePlus(new File(filePath).getName(), sanitizedList, 0, range, false, false, false, null); // Parallelisation occurs per file

        String totalPath = exportPath+File.separator+undistorted.getTitle();

        ImagePlusToOMETiff.writeToOMETiff(undistorted, new File(totalPath), 4, 2, "LZW");

        return totalPath;
    }

    public static void saveToXmlBdvDataset(Context ctx, List<String> filePaths, String filePath) throws ExecutionException, InterruptedException {
        String datasetName = filePath;
        File[] files = filePaths.stream().map(path -> new File(path)).toArray(File[]::new);
        AbstractSpimData spimdata = (AbstractSpimData) ctx.getService(CommandService.class).run(CreateBdvDatasetBioFormatsCommand.class,true,
                "datasetname", datasetName,
                "unit","MICROMETER",
                "files", files,
                "splitrgbchannels", false).get().getOutput("spimdata");

        new XmlFromSpimDataExporter(spimdata, filePath, ctx).run();

        SourceAndConverterService sac_service = ctx.getService(SourceAndConverterService.class);
        sac_service.remove(sac_service.getSourceAndConverterFromSpimdata(spimdata).toArray(new SourceAndConverter[0])); // Cleanup*/

    }

    private static FinalRealInterval bounds(SourceAndConverter source) {
        Interval interval = source.getSpimSource().getSource(0, 0);
        AffineTransform3D sourceTransform = new AffineTransform3D();
        source.getSpimSource().getSourceTransform(0, 0, sourceTransform);
        RealPoint corner0 = new RealPoint(new float[]{(float)interval.min(0), (float)interval.min(1), (float)interval.min(2)});
        RealPoint corner1 = new RealPoint(new float[]{(float)interval.max(0), (float)interval.max(1), (float)interval.max(2)});
        sourceTransform.apply(corner0, corner0);
        sourceTransform.apply(corner1, corner1);
        return new FinalRealInterval(new double[]{Math.min(corner0.getDoublePosition(0), corner1.getDoublePosition(0)), Math.min(corner0.getDoublePosition(1), corner1.getDoublePosition(1)), Math.min(corner0.getDoublePosition(2), corner1.getDoublePosition(2))}, new double[]{Math.max(corner0.getDoublePosition(0), corner1.getDoublePosition(0)), Math.max(corner0.getDoublePosition(1), corner1.getDoublePosition(1)), Math.max(corner0.getDoublePosition(2), corner1.getDoublePosition(2))});
    }

    private static boolean intersect2D(SourceAndConverter src1, SourceAndConverter src2) {
        RealInterval i1 = bounds(src1);
        RealInterval i2 = bounds(src2);
        if (i1.realMin(0)>i2.realMax(0)) {
            return false;
        }
        if (i2.realMin(0)>i1.realMax(0)) {
            return false;
        }
        if (i1.realMin(1)>i2.realMax(1)) {
            return false;
        }
        if (i2.realMin(1)>i1.realMax(1)) {
            return false;
        }
        return true;
    }

    public static List<String> getConnectedXmlDatasets(Context ctx, List<String> filePaths) {

        File parentPath = new File(new File(filePaths.get(0)).getParent());
        File[] files = filePaths.stream().map(path -> new File(path)).toArray(File[]::new);

        List<String> pathsOutput = new ArrayList<>();
        try {
            String datasetName = parentPath.getAbsolutePath();
            AbstractSpimData spimdata = (AbstractSpimData) ctx.getService(CommandService.class).run(CreateBdvDatasetBioFormatsCommand.class,true,
                    "datasetname", datasetName,
                    "unit","MICROMETER",
                    "files", files,
                    "splitrgbchannels", false).get().getOutput("spimdata");

            SourceAndConverterService sac_service = ctx.getService(SourceAndConverterService.class);

            SourceAndConverterServiceUI.Node filesNode =
                    sac_service.getUI()
                            .getRoot()
                            .child(datasetName)
                            .child(FileName.class.getSimpleName());

            spimdata.setBasePath(parentPath);

            Map<Integer, SourceAndConverter> fileIndexToSource = new HashMap<>();
            Map<Integer, Set<Integer>> links = new HashMap<>();
            Set<Integer> allNodes = new HashSet<>();

            for (int i = 0; i<filePaths.size(); i++) {
                fileIndexToSource.put(i, filesNode.child(i).sources()[0]); // We get a source per file
                Set<Integer> singleton = new HashSet<>();
                links.put(i, singleton);
                allNodes.add(i);
            }

            for (int i = 0; i<filePaths.size()-1; i++) {
                for (int j = i+1; j<filePaths.size();j++) {
                    SourceAndConverter srci = fileIndexToSource.get(i);
                    SourceAndConverter srcj = fileIndexToSource.get(j);
                    if (intersect2D(srci, srcj)) {
                        links.get(i).add(j);
                        links.get(j).add(i);
                    }
                }
            }

            sac_service.remove(sac_service.getSourceAndConverterFromSpimdata(spimdata).toArray(new SourceAndConverter[0])); // Cleanup*/

            List<Set<Integer>> components = new ArrayList<>();

            while(!allNodes.isEmpty()) {
                int startIndex = allNodes.iterator().next(); // Pick one
                LinkedList<Integer> unvisitedNodes = new LinkedList<>();
                Set<Integer> visitedNodes = new HashSet<>();
                unvisitedNodes.add(startIndex);
                while (unvisitedNodes.size() != 0) {
                    Integer cNode = unvisitedNodes.getFirst();
                    allNodes.remove(cNode);
                    visitedNodes.add(cNode);
                    links.get(cNode).stream().filter(n -> !visitedNodes.contains(n)).forEach(unvisitedNodes::add);
                    unvisitedNodes.removeFirst();
                }
                components.add(visitedNodes);
            }

            components.forEach(c -> {
                System.out.println("--- Component");
                c.forEach(System.out::println);
                List<String> filesInConnectedComponents = c.stream().map(i -> filePaths.get(i)).collect(Collectors.toList());
                int indexComponent = components.indexOf(c);
                File xmlFilePath = new File(parentPath, "bdvDataset_"+indexComponent+".xml");
                try {
                    saveToXmlBdvDataset(ctx, filesInConnectedComponents, xmlFilePath.getAbsolutePath());
                    pathsOutput.add(xmlFilePath.getAbsolutePath());
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return pathsOutput;
    }

    public static String getBigStitcherXmlDataset(Context context, String xmlDataset) {
        CommandService command = context.getService(CommandService.class);
        File fileIn = new File(xmlDataset);
        File fileOut = new File(fileIn.getParent(), FilenameUtils.removeExtension(fileIn.getName())+"-bigstitcher.xml");
        try {
            command.run(DatasetToBigStitcherDatasetCommand.class,true,
                    "xmlin", fileIn,
                            "xmlout", fileOut,
                    "viewsetupreference", -1
                    ).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return fileOut.getAbsolutePath();
    }

}
