package fused;

import bdv.util.BdvHandle;
import bdv.util.source.alpha.AlphaSourceHelper;
import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.EmptyMultiResolutionSourceAndConverterCreator;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import ch.epfl.biop.sourceandconverter.exporter.OMETiffExporter;
import ij.IJ;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imagej.ImageJ;
import net.imglib2.RealPoint;
import net.imglib2.display.ColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.ThinplateSplineTransform;
import net.imglib2.realtransform.Wrapped2DTransformAs3D;
import net.imglib2.realtransform.inverse.WrappedIterativeInvertibleRealTransform;
import ome.units.UNITS;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAutoAdjuster;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;
import spimdata.SpimDataHelper;

import javax.swing.tree.TreePath;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CrazyMultireWarpedSourcesFusedDemo {

    static ImageJ ij;

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ij = new ImageJ();
        ij.ui().showUI();

        DebugTools.enableLogging ("OFF");

        //DebugTools.enableLogging ("INFO");

        int nSourcesInX = 10;


        //final String filePath = "src/test/resources/mri-stack.xml";
        //final String filePath = "C:/Users/nicol/Desktop/demoabba/_bdvdataset_0.xml";//"src/test/resources/Icons.xml";
        final String filePath = "D:/QuPathDemoABBA/abba/_bdvdataset_0.xml";//"src/test/resources/Icons.xml";
        // Import SpimData
        SpimDataFromXmlImporter importer = new SpimDataFromXmlImporter(filePath);
        importer.run();

        TreePath tp =
                ij.get(SourceAndConverterService.class)
                        .getUI()
                        .getTreePathFromString(filePath+">Channel>1");

        List<SourceAndConverter> sources =
                ij.get(SourceAndConverterService.class).getUI().getSourceAndConvertersFromTreePath(tp);

        List<SourceAndConverter> sources_0 = new ArrayList<>();

        sources_0.addAll(demo(sources, nSourcesInX));

        tp =
                ij.get(SourceAndConverterService.class)
                        .getUI()
                        .getTreePathFromString(filePath+">Channel>2");

        sources =
                ij.get(SourceAndConverterService.class).getUI().getSourceAndConvertersFromTreePath(tp);


        List<SourceAndConverter> sources_1 = new ArrayList<>();

        sources_1.addAll(demo(sources, nSourcesInX));

        tp =
                ij.get(SourceAndConverterService.class)
                        .getUI()
                        .getTreePathFromString(filePath+">Channel>3");

        sources =
                ij.get(SourceAndConverterService.class).getUI().getSourceAndConvertersFromTreePath(tp);


        List<SourceAndConverter> sources_2 = new ArrayList<>();

        sources_2.addAll(demo(sources, nSourcesInX));

        double pxSize = 0.005*60;

        AffineTransform3D location = new AffineTransform3D();
        location.scale(pxSize);
        location.translate(0,0,0);

        int nPixX = (int) (400*nSourcesInX*0.5/pxSize);
        int nPixY = (int) (300*nSourcesInX*0.5/pxSize);

        IJ.log("Making a virtual ["+nPixX+"x"+nPixY+"] Image");

        SourceAndConverter model =
                new EmptyMultiResolutionSourceAndConverterCreator(
                        "Model",
                        location,
                        nPixX,
                        nPixY,
                        1,1,
                        2,2,2,5).get();

        SourceAndConverterServices.getSourceAndConverterService().register(model);

        SourceAndConverter fused_0 = new SourceFuserAndResampler(sources_0,
                AlphaFusedResampledSource.AVERAGE,
                model, "Fused source",
                true, true, false, 0,
                256, 256, 1, 3).get();

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        SourceAndConverterServices
                .getBdvDisplayService().show(bdvh, fused_0);

        new ViewerTransformAdjuster(bdvh, fused_0).run();

        SourceAndConverter fused_1 = new SourceFuserAndResampler(sources_1,
                AlphaFusedResampledSource.AVERAGE,
                model, "Fused source",
                true, true, false, 0,
                256, 256, 1, 3).get();


        SourceAndConverterServices
                .getBdvDisplayService().show(bdvh, fused_1);

        SourceAndConverter fused_2 = new SourceFuserAndResampler(sources_2,
                AlphaFusedResampledSource.AVERAGE,
                model, "Fused source",
                true, true, false, 0,
                256, 256, 1, 8).get();

        SourceAndConverterServices
                .getBdvDisplayService().show(bdvh, fused_2);

        try {
            DebugTools.setRootLevel("OFF");
            Instant start = Instant.now();
            OMETiffExporter.builder()
                    .millimeter()
                    //.savePath("C:\\Users\\nicol\\test.ome.tiff")
                    .savePath("C:\\Users\\chiarutt\\test.ome.tiff")
                    .tileSize(512,512)
                    .nThreads(8)
                    .create(fused_0, fused_1, fused_2).export();
            Instant finish = Instant.now();
            IJ.log("Duration: "+ Duration.between(start, finish));
            IJ.log("File saved");
        } catch (Exception e) {
            System.err.println("Error during saving");
            e.printStackTrace();
        }

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

    public static List<SourceAndConverter> demo(List<SourceAndConverter> sources, int numberOfSourcesInOneAxis) {

        ArrayList<SourceAndConverter> sacs = new ArrayList<>();

        int sourceIndex = 0;
        for (int x = 0; x < numberOfSourcesInOneAxis;x++) {
            for (int y = 0; y < numberOfSourcesInOneAxis; y++) {

                SourceAndConverter sac;
                sac = sources.get(sourceIndex);
                SourceAndConverter alphaSac = AlphaSourceHelper.getOrBuildAlphaSource(sac);

                AffineTransform3D at3d = new AffineTransform3D();

                at3d.rotate(2, Math.random()*6.0);
                at3d.scale((0.5 + Math.random() / 3.0)*25, (0.5 + Math.random() / 2.0)*25, 1);
                at3d.translate(200 * x, 200 * y, 0);

                RealPoint ptC = SourceAndConverterHelper.getSourceAndConverterCenterPoint(sac);
                double r = 2.0;
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

                        target[0][iPt] = xc+(xg*r+(Math.random()-0.5)*r)*0.8;
                        target[1][iPt] = yc+(yg*r+(Math.random()-0.5)*r)*0.8;

                        iPt++;
                    }
                }


                RealTransform rt = new ThinplateSplineTransform(target, origin);
                WrappedIterativeInvertibleRealTransform invertibleRealTransform = new WrappedIterativeInvertibleRealTransform(rt);
                Wrapped2DTransformAs3D rt3d = new Wrapped2DTransformAs3D(invertibleRealTransform);

                SourceRealTransformer srt = new SourceRealTransformer(sac, rt3d);
                srt.run();
                SourceAndConverter warped_sac = srt.getSourceOut();
                SourceAndConverterServices
                        .getSourceAndConverterService()
                                .register(warped_sac);

                AlphaSourceHelper.setAlphaSource(warped_sac, alphaSac); // Keeps bounds

                SourceAffineTransformer sat = new SourceAffineTransformer(warped_sac, at3d);
                sat.run();

                SourceAndConverter transformedSac = sat.getSourceOut();

                sacs.add(transformedSac);
                sourceIndex++;
                sourceIndex = sourceIndex % sources.size();
            }
        }

        return sacs;
    }
}
