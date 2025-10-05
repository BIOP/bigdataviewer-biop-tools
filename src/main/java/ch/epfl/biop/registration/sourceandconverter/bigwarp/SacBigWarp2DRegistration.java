package ch.epfl.biop.registration.sourceandconverter.bigwarp;

import bdv.gui.TransformTypeSelectDialog;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.plugin.IRegistrationPlugin;
import ch.epfl.biop.registration.plugin.RegistrationTypeProperties;
import ch.epfl.biop.registration.sourceandconverter.spline.RealTransformSourceAndConverterRegistration;
import ij.gui.WaitForUserDialog;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.register.BigWarpLauncher;

import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static bdv.util.RealTransformHelper.BigWarpFileFromRealTransform;
import static ch.epfl.biop.registration.sourceandconverter.spline.Elastix2DSplineRegistration.showAndWait;

@Plugin(type = IRegistrationPlugin.class)
@RegistrationTypeProperties(
        isManual = true,
        isEditable = true
)
public class SacBigWarp2DRegistration extends RealTransformSourceAndConverterRegistration {

    BigWarpLauncher bwl;

    Runnable waitForUser = () -> {

        String helpMessage = "<html><body width='420'>" +
                "<h2>BigWarp Registration</h2>" +
                "<p>Place landmark pairs on corresponding points in both images to create your transformation.</p>" +
                "<p><b>Key Controls:</b></p>" +
                "<ul>" +
                "<li><b>Space</b> — Toggle between Landmark mode and Navigation mode</li>" +
                "<li><b>Click</b> (in Landmark mode) — Add or move landmark points</li>" +
                "<li><b>Ctrl + Click</b> — Pin a point (same location in both images)</li>" +
                "<li><b>T</b> — Toggle warped/raw view of moving image</li>" +
                "<li><b>E</b> — Center view on nearest landmark</li>" +
                "<li><b>Q/W</b> — Align viewers to each other</li>" +
                "<li><b>Delete</b> — Remove selected landmark from table</li>" +
                "<li><b>Ctrl + Z/Y</b> — Undo/Redo landmark changes</li>" +
                "</ul>" +
                "<p><b>When satisfied with the alignment, press OK.</b></p>" +
                "<p style='margin-top:10px;'><small>More info: <a href='https://imagej.net/plugins/bigwarp'>BigWarp Documentation</a></small></p>" +
                "</body></html>";

        try {
            showAndWait(bwl.getBigWarp().getLandmarkFrame(),
                    helpMessage,
                    "BigWarp Registration");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    };

    public void setWaitForUserMethod(Runnable r) {
        waitForUser = r;
    }

    @Override
    public boolean register() {
        {

            try {
                EventQueue.invokeAndWait(() -> {
                    List<SourceAndConverter<?>> movingSacs = Arrays.stream(mimg).collect(Collectors.toList());

                    List<SourceAndConverter<?>> fixedSacs = Arrays.stream(fimg).collect(Collectors.toList());

                    List<ConverterSetup> converterSetups = Arrays.stream(mimg).map(src -> SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(src)).collect(Collectors.toList());

                    converterSetups.addAll(Arrays.stream(fimg).map(src -> SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(src)).collect(Collectors.toList()));

                    // Launch BigWarp
                    bwl = new BigWarpLauncher(movingSacs, fixedSacs, "Big Warp", converterSetups);
                    bwl.set2d();
                    bwl.run();

                    // Output bdvh handles -> will be put in the object service
                    BdvHandle bdvhQ = bwl.getBdvHandleQ();
                    BdvHandle bdvhP = bwl.getBdvHandleP();

                    bdvhQ.getViewerPanel().state().setDisplayMode(DisplayMode.FUSED);
                    bdvhP.getViewerPanel().state().setDisplayMode(DisplayMode.FUSED);

                    bdvhP.getViewerPanel().state().setViewerTransform(BdvHandleHelper.getViewerTransformWithNewCenter(bdvhP, new double[]{0,0,0}));
                    bdvhQ.getViewerPanel().state().setViewerTransform(BdvHandleHelper.getViewerTransformWithNewCenter(bdvhQ, new double[]{0,0,0}));

                    SourceAndConverterServices.getBdvDisplayService().pairClosing(bdvhQ,bdvhP);

                    bdvhP.getViewerPanel().requestRepaint();
                    bdvhQ.getViewerPanel().requestRepaint();

                    bwl.getBigWarp().getLandmarkFrame().repaint();

                    // Restores landmarks if some were already defined
                    if (rt!=null) {
                        String bigWarpFile = BigWarpFileFromRealTransform(rt);
                        if (bigWarpFile!=null) { // If the transform is not a spline, no landmarks are saved, the user has to redo his job
                            bwl.getBigWarp().loadLandmarks(bigWarpFile);
                            bwl.getBigWarp().setInLandmarkMode(true);
                            bwl.getBigWarp().setIsMovingDisplayTransformed(true);
                        }
                    }
                });
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }

            waitForUser.run();

            switch (bwl.getBigWarp().getBwTransform().getTransformType()) {
                case (TransformTypeSelectDialog.TPS) :
                    // Thin plate spline transform
                    rt = bwl.getBigWarp().getBwTransform().getTransformation();
                    break;
                default:
                    // Any other transform, currently Affine 3D
                    rt = bwl.getBigWarp().getBwTransform().affine3d();
            }

            bwl.getBigWarp().closeAll();

            isDone = true;

            return true;

        }
    }

    @Override
    public boolean edit() {
        // Just launching again BigWarp
        this.register();
        return true;
    }

    @Override
    public void abort() {

    }


    public String toString() {
        return "Big Warp";
    }

}
