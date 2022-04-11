import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.command.BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand;
import ch.epfl.biop.sourceandconverter.processor.SourcesResampler;
import ij.ImagePlus;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.*;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.ISourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import ch.epfl.biop.sourceandconverter.processor.SourcesAffineTransformer;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceRealTransformer;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class DemoTomoSlice {

    static {
        LegacyInjector.preinit();
    }

    // https://forum.image.sc/t/reconstructing-tomographic-light-sheet-data-2d-slice-into-cylindrical-3d-volume/65616/2
    static public void main(String... args) {
        // create the ImageJ application context with all available services
        DebugTools.enableIJLogging(false);
        //DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();

        DebugTools.enableLogging ("OFF");
        ij.ui().showUI();

        ISourceAndConverterService sourceService = SourceAndConverterServices.getSourceAndConverterService();

        SourceAndConverterBdvDisplayService displayService = SourceAndConverterServices.getBdvDisplayService();

        try {

            AbstractSpimData sd = (AbstractSpimData) ij.command().run(BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand.class, true,
                    "unit","MILLIMETER",
                    "splitrgbchannels", false
                    ).get().getOutput("spimdata");

            SourceAndConverter[] sources = sourceService.getSourceAndConverterFromSpimdata(sd).toArray(new SourceAndConverter[0]);
            ISourceAndConverterService
            AffineTransform3D center = new AffineTransform3D();
            center.translate(-128,-128,-192/2.0); // Recenter
            center.rotate(0, Math.PI/2.0); // Rotate axis correctly
            sources = new SourcesAffineTransformer(center).apply(sources);
            RealPoint pt = SourceAndConverterHelper.getSourceAndConverterCenterPoint(sources[0]);
            center.translate(pt.positionAsDoubleArray());


            InvertibleRealTransform pol2car = new Wrapped2DTransformAs3D(new PolarToCartesianTransform2D());
            SourceRealTransformer srt = new SourceRealTransformer(null, pol2car);
            SourceAndConverter[] transformedSources = Arrays.stream(sources).map(srt).collect(Collectors.toList()).toArray(new SourceAndConverter[0]);

            SourceAndConverter model = new EmptySourceAndConverterCreator("Model",
                    new FinalRealInterval(new double[]{-128,0,-128}, new double[]{128,Math.PI,128}),1,Math.PI/5000.0,1)
                    .get();

            SourceAndConverter[] resampledSources = new SourcesResampler(model).apply(transformedSources);

            SourceRealTransformer srt_inv = new SourceRealTransformer(null, pol2car.inverse());
            SourceAndConverter[] transformedSources_Inv = Arrays.stream(resampledSources).map(srt_inv).collect(Collectors.toList()).toArray(new SourceAndConverter[0]);

            new ViewerTransformAdjuster()

            BdvHandle winOrigin = displayService.getNewBdv();
            BdvHandle winTomo = displayService.getNewBdv();
            BdvHandle winTomoResampled = displayService.getNewBdv();

            displayService.show(winOrigin, sources);
            displayService.show(winTomo, transformedSources);
            displayService.show(winTomoResampled, resampledSources);

            displayService.show(winOrigin,transformedSources_Inv);
            ImageJFunctions.show(resampledSources  [0].getSpimSource().getSource(0,0));
            ImagePlus imp;

            imp.getNSlices();

        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }







        //SpimDataPostprocessor p;
        //System.out.println("Done.");*/
        //IJ.run("Open [BioFormats Bdv Bridge (Basic)]", "unit=MILLIMETER splitrgbchannels=false");

        /*((SourceAndConverterService)(SourceAndConverterServices
                .getSourceAndConverterService()))
                .registerScijavaCommand(ExportToImagePlusCommand.class);*/

        /*Source bdvsrc = BioFormatsBdvOpener
                .getOpener().auto()
                .positionReferenceFrameLength(new Length(10, UNITS.MICROMETER))
                .voxSizeReferenceFrameLength(new Length(10, UNITS.MICROMETER))
                .location("C:\\Users\\nicol\\Dropbox\\BIOP\\19-05-24 VSI Samples\\align\\Image_05.vsi")
                .getVolatileSources("2.0").get(0);

        BdvStackSource bss = BdvFunctions.show(bdvsrc, BdvOptions.options());
        bss.setDisplayRange(0,250);
        BdvHandle bdvh = bss.getBdvHandle();
        ij.object().addObject(bdvh);


        /*AffineTransform3D at3dmm0 = new AffineTransform3D();
        bdvsrc.getSourceTransform(0,0,at3dmm0);
        System.out.println("at3dmm0:"+at3dmm0);

        AffineTransform3D at3dmm4 = new AffineTransform3D();
        bdvsrc.getSourceTransform(0,4,at3dmm4);
        System.out.println("at3dmm4:"+at3dmm4);

        AffineTransform3D mm4 = new AffineTransform3D();
        mm4.concatenate(at3dmm0.inverse());
        mm4.concatenate(at3dmm4);
        System.out.println("mm4:"+mm4);


        AffineTransform3D at3d = new AffineTransform3D();
        at3d.concatenate(at3dmm0.inverse());

        AffineTransform3D trInLocMM4 = new AffineTransform3D();
        trInLocMM4.translate(-1,-1,0);
        //trInLocMM4.concatenate(mm4.inverse());
        //trInLocMM4.preConcatenate(mm4);
        at3d.preConcatenate(trInLocMM4);
        at3d.preConcatenate(at3dmm0);*/

        //System.out.println("at3d:"+at3d);

/*
        try {

            AffineTransform3D a = (AffineTransform3D) ij.command().run(FindLineSymmetry2D.class,true,
                    "sourceIndex",0,
                    "bdv_h",bdvh,
                    "numMipMap",6,
                    "timepoint",0
            ).get().getOutput("at3D");

            System.out.println(a);
            ij.module().run(ij.command().getCommand(BDVSourceAffineTransform.class),true,
                    "sourceIndexString","0",
                    "bdv_h_in",bdvh,
                    "bdv_h_out",bdvh,
                    "at",a,
                    "output_mode", BDVSourceAndConverterFunctionalInterfaceCommand.ADD,
                    "transformInPlace", false
                    ).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }*/

    }

    /*
    #@ ObjectService os
#@ ImagePlus imp

import ch.epfl.biop.bdv.process.Procedural3DImageShort
import bdv.util.BdvFunctions
import bdv.util.BdvOptions
import bdv.util.BdvHandle

bdv_h = os.getObjects(BdvHandle.class).get(0)


/*
s = (new Procedural3DImageShort(
            { p ->


            	(int) ((Math.sin(p[0]*4.0)*Math.cos(p[1]*20.0)+1)*100)

            }
      )).getSource("Wave");

BdvFunctions.show( s, BdvOptions.options().addTo(bdv_h) );

     */
}
