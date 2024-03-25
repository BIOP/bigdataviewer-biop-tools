package fused;

import bdv.util.BdvHandle;
import bdv.util.source.alpha.AlphaSourceHelper;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ch.epfl.biop.sourceandconverter.EmptyMultiResolutionSourceAndConverterCreator;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import ij.IJ;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleWrapped2DTransformAs3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import net.imglib2.type.numeric.real.FloatType;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CrazyMultireWarpedSourcesFusedDemo {

    static ImageJ ij;

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ij = new ImageJ();
        ij.ui().showUI();

        DebugTools.enableLogging ("OFF");

        //DebugTools.enableLogging ("INFO");

        int nSourcesInX = 20;
        String folderVsi;
        String datasetName = "brain_slices";
        try {
            folderVsi = DatasetHelper.dowloadBrainVSIDataset();


            ij.command().run(CreateBdvDatasetBioFormatsCommand.class, true,
                "files", new File[] {
                            new File(folderVsi+"Slide_00.vsi"),
                            new File(folderVsi+"Slide_01.vsi"),
                            new File(folderVsi+"Slide_02.vsi"),
                            new File(folderVsi+"Slide_03.vsi"),
                            new File(folderVsi+"Slide_04.vsi"),
                            new File(folderVsi+"Slide_05.vsi"),
                            new File(folderVsi+"Slide_06.vsi")},
                    "datasetname", datasetName,
                    "unit", "MILLIMETER",
                    "split_rgb_channels", false,
                    "plane_origin_convention", "CENTER").get();
        } catch (Exception exception) {
            exception.printStackTrace();
            return;
        }

        List<SourceAndConverter<?>> sources =
                ij.get(SourceAndConverterService.class).getUI()
                  .getSourceAndConvertersFromPath(datasetName+">Channel>FL DAPI");

        List<SourceAndConverter<?>> sources_0 = new ArrayList<>();

        sources_0.addAll(demo(sources, nSourcesInX));

        sources = ij.get(SourceAndConverterService.class).getUI()
                    .getSourceAndConvertersFromPath(datasetName+">Channel>FL FITC");

        List<SourceAndConverter<?>> sources_1 = new ArrayList<>();
        sources_1.addAll(demo(sources, nSourcesInX));

        sources = ij.get(SourceAndConverterService.class).getUI()
                    .getSourceAndConvertersFromPath(datasetName+">Channel>FL CY3");

        List<SourceAndConverter<?>> sources_2 = new ArrayList<>();
        sources_2.addAll(demo(sources, nSourcesInX));

        //sources_2.forEach(SourceAndConverterServices.getSourceAndConverterService()::register);

        double pxSize = 0.0002;//*10;

        AffineTransform3D location = new AffineTransform3D();
        location.scale(pxSize);
        location.translate(0,0,0);

        int nPixX = (int) (20*nSourcesInX*0.5/pxSize);
        int nPixY = (int) (15*nSourcesInX*0.5/pxSize);

        IJ.log("Making a virtual ["+nPixX+"x"+nPixY+"] Image");

        SourceAndConverter<?> model =
                new EmptyMultiResolutionSourceAndConverterCreator(
                        "Model",
                        location,
                        nPixX,
                        nPixY,
                        1,1,
                        2,2,2,16).get();

        SourceAndConverterServices.getSourceAndConverterService().register(model);
        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();


        SourceAndConverter<?> fused_0 = new SourceFuserAndResampler(sources_0,
                //AlphaFusedResampledSource.AVERAGE,
                AlphaFusedResampledSource.SUM,
                model, "Fused source",
                true, true, false, 0,
                256, 256, 1, -1,8).get();


        SourceAndConverterServices.getBdvDisplayService().show(bdvh, fused_0);

        new ViewerTransformAdjuster(bdvh, fused_0).run();

        SourceAndConverter<?> fused_1 = new SourceFuserAndResampler(sources_1,
                //AlphaFusedResampledSource.AVERAGE,
                AlphaFusedResampledSource.SUM,
                model, "Fused source",
                true, true, false, 0,
                256, 256, 1, -1, 8).get();


        SourceAndConverterServices.getBdvDisplayService().show(bdvh, fused_1);

        SourceAndConverter<?> fused_2 = new SourceFuserAndResampler(sources_2,
                //AlphaFusedResampledSource.AVERAGE,
                AlphaFusedResampledSource.SUM,
                model, "Fused source",
                true, true, false, 0,
                256, 256, 1, -1,8).get();

        SourceAndConverterServices.getBdvDisplayService().show(bdvh, fused_2);

        /*try {
            DebugTools.setRootLevel("OFF");
            Instant start = Instant.now();
            OMETiffExporter.builder()
                    .millimeter()
                    //.savePath("C:\\Users\\nicol\\test.ome.tiff")
                    .savePath("C:\\Users\\chiarutt\\test.ome.tiff")
                    .tileSize(512,512)
                    .lzw()
                    .nThreads(8)
                    .create(fused_0, fused_1, fused_2).export();
            Instant finish = Instant.now();
            IJ.log("Duration: "+ Duration.between(start, finish));
            IJ.log("File saved");
        } catch (Exception e) {
            System.err.println("Error during saving");
            e.printStackTrace();
        }*/

        /*OMETiffExporter exporter = OMETiffExporter.builder()
                .lzw()
                .tileSize(512,512)
                .savePath("C:\\Users\\chiarutt\\test.ome.tiff")
                .millimeter()
                .create(fused_0, fused_1, fused_2);

        new Thread(() -> {
            try {
                Instant start = Instant.now();
                exporter.export();
                Instant finish = Instant.now();
                IJ.log("Duration: "+Duration.between(start, finish));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        while (exporter.getWrittenTiles() < exporter.getTotalTiles()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            IJ.log("Export to OME TIFF: "+exporter.getWrittenTiles()+"/"+exporter.getTotalTiles()+" tiles written");
        }
        IJ.log("File saved");*/

    }

    @Test
    public void demoRunOk() {
        main(new String[]{""});
    }

    @After
    public void closeFiji() {
        TestHelper.closeFijiAndBdvs(ij);
    }

    static int ini = 0;

    public static List<SourceAndConverter<?>> demo(List<SourceAndConverter<?>> sources, int numberOfSourcesInOneAxis) {

        ArrayList<SourceAndConverter<?>> sacs = new ArrayList<>();
        //ini++;
        Random generator = new Random(200+ini);

        int sourceIndex = 0;
        for (int x = 0; x < numberOfSourcesInOneAxis;x++) {
            for (int y = 0; y < numberOfSourcesInOneAxis; y++) {
                // For each source

                SourceAndConverter<?> sac = sources.get(sourceIndex);
                SourceAndConverter<FloatType> alphaSac = AlphaSourceHelper.getOrBuildAlphaSource(sac);
                RealPoint center = SourceAndConverterHelper.getSourceAndConverterCenterPoint(sac, 0);
                AffineTransform3D at3d = new AffineTransform3D();

                //at3d.rotate(2, generator.nextDouble()*6.0); // Rotate along Z
                //at3d.scale((0.5 + generator.nextDouble() / 3.0)*2, (0.5 + generator.nextDouble() / 2.0)*2, 1); // Random scaling
                at3d.translate(8 * (x+1)-center.getDoublePosition(0),
                        6 * (y+1)-center.getDoublePosition(1), -center.getDoublePosition(2)); // random translation

                RealPoint ptC = SourceAndConverterHelper.getSourceAndConverterCenterPoint(sac,0);
                double r = 1;
                double xc = ptC.getDoublePosition(0);
                double yc = ptC.getDoublePosition(1);

                int nPtX = 3;
                int nPtY = 3;

                int nPt = (2*nPtX+1)*(2*nPtY+1);


                double[][] origin = new double[2][5];
                double[][] target = new double[2][5];

                int iPt = 0;

                origin[0] = new double[nPt];
                origin[1] = new double[nPt];


                target[0] = new double[nPt];
                target[1] = new double[nPt];

                for (int xg = -nPtX;xg<=nPtX;xg++) {
                    for (int yg = -nPtY;yg<=nPtY;yg++) {
                        origin[0][iPt] = xc+xg*r;
                        origin[1][iPt] = yc+yg*r;

                        target[0][iPt] = xc+xg*(generator.nextDouble()/10.0+0.8)*r;//origin[0][iPt];//xc+(xg*r+(generator.nextDouble()-1)*r)*0.8;
                        target[1][iPt] = yc+yg*(generator.nextDouble()/10.0+0.8)*r;//origin[1][iPt];//yc+(yg*r+(generator.nextDouble()-1)*r)*0.8;

                        iPt++;
                    }
                }

                // Random warping
                RealTransform rt = new ThinplateSplineTransform(target, origin);
                WrappedIterativeInvertibleRealTransform invertibleRealTransform = new WrappedIterativeInvertibleRealTransform(rt);
                InvertibleWrapped2DTransformAs3D rt3d = new InvertibleWrapped2DTransformAs3D(invertibleRealTransform);

                SourceAndConverter<?> warped_sac = new SourceRealTransformer<>(sac, rt3d).get();
                SourceAndConverterServices
                        .getSourceAndConverterService()
                                .register(warped_sac);

                AlphaSourceHelper.setAlphaSource(warped_sac, alphaSac); // Keeps bounds
                SourceAndConverter<?> transformedSac = new SourceAffineTransformer<>(warped_sac, at3d).get();

                sacs.add(transformedSac);
                sourceIndex++;
                sourceIndex = sourceIndex % sources.size();
            }
        }

        return sacs;
    }
}
