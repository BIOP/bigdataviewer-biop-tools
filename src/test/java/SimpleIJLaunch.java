
import bdv.util.RealTransformHelper;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.command.BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand;
import ij.IJ;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.convert.ConvertService;
import org.scijava.task.DefaultTaskService;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.scijava.processors.SpimDataPostprocessor;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import java.util.List;
import java.util.concurrent.ExecutionException;


public class SimpleIJLaunch {

    static {
        LegacyInjector.preinit();
    }

    static public void main(String... args) {
        // create the ImageJ application context with all available services
        //DebugTools.enableIJLogging(false);
        //DebugTools.enableLogging("INFO");

        final ImageJ ij = new ImageJ();

        DebugTools.enableLogging ("OFF");
        ij.ui().showUI();
        /*TaskService taskService = ij.get(TaskService.class);

        Task task = taskService.createTask("Coucou");

        task.setStatusMessage("Starting task");
        task.setProgressMaximum(100);
        task.setProgressValue(10);*/



        //DebugTools.enableIJLogging(true);
        //DebugTools.setRootLevel("DEBUG");

       /* try {
            ij.command().run(BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand.class, true,
                    "unit","MILLIMETER",
                    "splitrgbchannels", false
                    ).get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        SpimDataPostprocessor p;
        System.out.println("Done.");*/
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
