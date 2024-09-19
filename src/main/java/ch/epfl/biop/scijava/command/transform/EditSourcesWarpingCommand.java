package ch.epfl.biop.scijava.command.transform;

import bdv.img.WarpedSource;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ij.gui.WaitForUserDialog;
import net.imglib2.realtransform.RealTransform;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.register.BigWarpLauncher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static bdv.util.RealTransformHelper.BigWarpFileFromRealTransform;

@Plugin(type = BdvPlaygroundActionCommand.class/*,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Edit Sources Warping"*/)

public class EditSourcesWarpingCommand implements BdvPlaygroundActionCommand {

    @Parameter(type = ItemIO.BOTH)
    SourceAndConverter<?>[] moving_sources;


    @Parameter(required = false)
    SourceAndConverter<?>[] fixed_sources;

    @Parameter
    boolean is2d;

    Runnable waitForUser = () -> {
        WaitForUserDialog dialog = new WaitForUserDialog("Choose slice","Please perform carefully your registration then press ok.");
        dialog.show();
    };

    @Override
    public void run() {
        for (SourceAndConverter sac : moving_sources) {
            if (!(sac.getSpimSource() instanceof WarpedSource)) {
                System.err.println(sac.getSpimSource().getName()+" is not a Warped source, it cannot be edited");
            }
        }

        RealTransform rt = ((WarpedSource) moving_sources[0].getSpimSource()).getTransform();

        List<SourceAndConverter<?>> movingSacs = Arrays.stream(moving_sources).collect(Collectors.toList());

        List<ConverterSetup> converterSetups = Arrays.stream(moving_sources).map(src -> SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(src)).collect(Collectors.toList());
        List<SourceAndConverter<?>> fixedSacs;

        if (fixed_sources !=null) {
            fixedSacs = Arrays.stream(fixed_sources).collect(Collectors.toList());
            converterSetups.addAll(Arrays.stream(fixed_sources).map(src -> SourceAndConverterServices.getSourceAndConverterService().getConverterSetup(src)).collect(Collectors.toList()));
        } else {
            fixedSacs = new ArrayList<>();
        }

        // Launch BigWarp
        BigWarpLauncher bwl = new BigWarpLauncher(movingSacs, fixedSacs, "Big Warp", converterSetups);
        bwl.set2d();
        bwl.run();

        // Output bdvh handles -> will be put in the object service
        BdvHandle bdvhQ = bwl.getBdvHandleQ();
        BdvHandle bdvhP = bwl.getBdvHandleP();

        bdvhP.getViewerPanel().state().setViewerTransform(BdvHandleHelper.getViewerTransformWithNewCenter(bdvhP, new double[]{0,0,0}));
        bdvhQ.getViewerPanel().state().setViewerTransform(BdvHandleHelper.getViewerTransformWithNewCenter(bdvhQ, new double[]{0,0,0}));

        SourceAndConverterServices.getBdvDisplayService().pairClosing(bdvhQ,bdvhP);

        bdvhP.getViewerPanel().requestRepaint();
        bdvhQ.getViewerPanel().requestRepaint();

        bwl.getBigWarp().getLandmarkFrame().repaint();

        bwl.getBigWarp().loadLandmarks(BigWarpFileFromRealTransform(rt));
        //bwl.getBigWarp().setInLandmarkMode(true);
        bwl.getBigWarp().setIsMovingDisplayTransformed(true);

        waitForUser.run();

        rt = bwl.getBigWarp().getBwTransform().getTransformation();

        bwl.getBigWarp().closeAll();

        for (SourceAndConverter sac : moving_sources) {
            WarpedSource src = ((WarpedSource) sac.getSpimSource());
            src.updateTransform(rt);
            src.setIsTransformed(true);
            if (sac.asVolatile() != null) {
                WarpedSource vsrc = (WarpedSource) sac.asVolatile().getSpimSource();
                vsrc.updateTransform(rt);
                vsrc.setIsTransformed(true);
            }
        }

    }
}
