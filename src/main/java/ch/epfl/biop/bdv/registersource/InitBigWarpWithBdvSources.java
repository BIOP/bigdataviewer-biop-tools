package ch.epfl.biop.bdv.registersource;

import bdv.util.BWBdvHandle;
import bdv.util.BdvHandle;
import bdv.viewer.Source;
import bigwarp.BigWarp;
import bigwarp.BigWarpInit;

import java.util.logging.Logger;
import java.util.stream.Collectors;

import ch.epfl.biop.bdv.commands.CommandHelper;
import mpicbg.spim.data.SpimDataException;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;


@Plugin(type = Command.class,menuPath = "Plugins>BigDataViewer>SciJava>BigWarp (SciJava)")
public class InitBigWarpWithBdvSources implements Command {

    private static final Logger LOGGER = Logger.getLogger( InitBigWarpWithBdvSources.class.getName() );

    @Parameter(type = ItemIO.OUTPUT)
    BdvHandle bdvHandleP;

    @Parameter(type = ItemIO.OUTPUT)
    BdvHandle bdvHandleQ;

    @Parameter
    BdvHandle bdv_h_fixed;

    @Parameter(label="Fixed source indexes ('2,3-5'), starts at 0")
    String idx_src_fixed;

    @Parameter
    BdvHandle bdv_h_moving;

    @Parameter(label="Moving source indexes ('2,3-5'), starts at 0")
    String idx_src_moving;

    @Override
    public void run() {
        // TODO fix names, transfer converters

        Source<?>[] fxSrcs =
                CommandHelper.commaSeparatedListToArray(idx_src_fixed)
                        .stream()
                        .map(idx -> bdv_h_fixed.getViewerPanel().getState().getSources().get(idx).getSpimSource())
                        .collect(Collectors.toList())
                        .toArray(new Source<?>[]{});

        Source<?>[] mvSrcs =
                CommandHelper.commaSeparatedListToArray(idx_src_moving)
                        .stream()
                        .map(idx -> bdv_h_moving.getViewerPanel().getState().getSources().get(idx).getSpimSource())
                        .collect(Collectors.toList())
                        .toArray(new Source<?>[]{});



        String[] dummyNames = new String[mvSrcs.length + fxSrcs.length];

        for (int i = 0; i < dummyNames.length; i++) {
            // TODO transfer names
            dummyNames[i] = "Src " + i;
        }

        BigWarp.BigWarpData<?> bwd = BigWarpInit.createBigWarpData(mvSrcs, fxSrcs, dummyNames);

        try {
            BigWarp<?> bw = new BigWarp(bwd, "Big Warp", null);
            bw.getViewerFrameP().setVisible(true);
            bw.getViewerFrameQ().setVisible(true);

            bdvHandleP = new BWBdvHandle(bw, true);
            bdvHandleQ = new BWBdvHandle(bw, false);



        } catch (SpimDataException e) {
            e.printStackTrace();
        }
    }

}
