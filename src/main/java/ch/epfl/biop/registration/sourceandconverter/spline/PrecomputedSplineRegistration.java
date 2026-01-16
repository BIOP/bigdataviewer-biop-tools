package ch.epfl.biop.registration.sourceandconverter.spline;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.plugin.IRegistrationPlugin;
import ch.epfl.biop.registration.plugin.RegistrationTypeProperties;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.register.BigWarpLauncher;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static bdv.util.RealTransformHelper.BigWarpFileFromRealTransform;
import static ch.epfl.biop.registration.sourceandconverter.spline.Elastix2DSplineRegistration.showAndWait;

/**
 * A registration class that accepts a pre-computed spline/deformable transform
 * without running any registration algorithm. Used for loading saved registrations.
 */
@Plugin(type = IRegistrationPlugin.class)
@RegistrationTypeProperties(
        isManual = false,
        isEditable = true
)
public class PrecomputedSplineRegistration extends RealTransformSourceAndConverterRegistration {

    public static final String TRANSFORM_KEY = "transform";

    String exceptionMessage;

    @Override
    public boolean register() {
        // The transform is already provided in parameters, just deserialize it
        String serializedTransform = parameters.get(TRANSFORM_KEY);
        if (serializedTransform == null) {
            exceptionMessage = "No transform provided in parameters";
            return false;
        }

        try {
            setTransform(serializedTransform);
            isDone = true;
            return true;
        } catch (Exception e) {
            exceptionMessage = "Failed to deserialize transform: " + e.getMessage();
            return false;
        }
    }

    @Override
    public void abort() {
        // Nothing to abort
    }

    String name = "Precomputed Spline";

    @Override
    public void setRegistrationName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    BigWarpLauncher bwl;

    @Override
    public boolean edit() {
        try {
            EventQueue.invokeAndWait(() -> {
                java.util.List<SourceAndConverter<?>> movingSacs = Arrays.stream(mimg).collect(Collectors.toList());

                java.util.List<SourceAndConverter<?>> fixedSacs = Arrays.stream(fimg).collect(Collectors.toList());

                List<ConverterSetup> converterSetups = Arrays.stream(mimg).map(src -> SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(src)).collect(Collectors.toList());

                converterSetups.addAll(Arrays.stream(fimg).map(src -> SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(src)).collect(Collectors.toList()));

                // Launch BigWarp
                bwl = new BigWarpLauncher(movingSacs, fixedSacs, "Big Warp", converterSetups);
                bwl.set2d();
                bwl.run();

                // Output bdvh handles -> will be put in the object service
                BdvHandle bdvhQ = bwl.getBdvHandleQ();
                BdvHandle bdvhP = bwl.getBdvHandleP();

                bdvhP.getViewerPanel().state().setViewerTransform(BdvHandleHelper.getViewerTransformWithNewCenter(bdvhP, new double[]{0,0,0}));
                bdvhQ.getViewerPanel().state().setViewerTransform(BdvHandleHelper.getViewerTransformWithNewCenter(bdvhQ, new double[]{0,0,0}));

                bdvhQ.getViewerPanel().state().setDisplayMode(bdv.viewer.DisplayMode.FUSED);
                bdvhP.getViewerPanel().state().setDisplayMode(DisplayMode.FUSED);

                SourceAndConverterServices.getBdvDisplayService().pairClosing(bdvhQ,bdvhP);

                bdvhP.getViewerPanel().requestRepaint();
                bdvhQ.getViewerPanel().requestRepaint();

                bwl.getBigWarp().getLandmarkFrame().repaint();

                if (rt!=null) {
                    bwl.getBigWarp().loadLandmarks(BigWarpFileFromRealTransform(rt));
                    bwl.getBigWarp().setIsMovingDisplayTransformed(true);
                }
            });
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        String helpMessage = "<html><body width='420'>" +
                "<h2>BigWarp Registration</h2>" +
                "<p><b>Key Controls:</b></p>" +
                "<ul>" +
                "<li><b>Space</b> — Toggle between Landmark mode and Navigation mode</li>" +
                "<li><b>Click</b> (in Landmark mode) — Move landmark point</li>" +
                "<li><b>Ctrl + Click</b> — Create a new landmark</li>" +
                "<li><b>T</b> — Toggle warped/raw view of moving image</li>" +
                "<li><b>E</b> — Center view on selected landmark</li>" +
                "<li><b>Delete</b> — Remove selected landmark from table</li>" +
                "<li><b>Ctrl + Z/Y</b> — Undo/Redo landmark changes</li>" +
                "</ul>" +
                "<p><b>When satisfied with the alignment, press OK.</b></p>" +
                "<p style='margin-top:10px;'><small>More info: <a href='https://imagej.net/plugins/bigwarp'>BigWarp Documentation</a></small></p>" +
                "</body></html>";

        try {
            showAndWait(bwl.getBigWarp().getLandmarkFrame(),
                    helpMessage,
                    "Edit Spline Registration");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        rt = bwl.getBigWarp().getBwTransform().getTransformation();

        bwl.getBigWarp().closeAll();

        isDone = true;

        return true;

    }
}
