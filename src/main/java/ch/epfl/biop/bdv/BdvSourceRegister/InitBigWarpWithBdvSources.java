package ch.epfl.biop.bdv.BdvSourceRegister;

import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.util.BdvHandle;
import bdv.viewer.Source;
import bigwarp.BigWarp;
import bigwarp.BigWarpInit;
//import mpicbg.spim.data.generic.AbstractSpimData;
//import mpicbg.spim.data.sequence.TimePoints;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import mpicbg.spim.data.SpimDataException;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;


@Plugin(type = Command.class,menuPath = "Plugins>BigDataViewer>SciJava>BigWarp (SciJava)")

public class InitBigWarpWithBdvSources implements Command {

    @Parameter
    BdvHandle bdv_h;

    @Parameter
    int idx_src_moving;

    @Parameter
    int idx_src_fixed;

    @Override
    public void run() {
        //try {
            //Path tempDir = Files.createTempDirectory("testSpimdata");

            //final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepointMap ), setupMap, null, null );

            //BigWarpInit
            //BigWarpInit.initSetups()
            //BigWarpInit

            Source<?> movingSrc = bdv_h.getViewerPanel().getState().getSources().get(idx_src_moving).getSpimSource();
            Source<?> fixedSrc = bdv_h.getViewerPanel().getState().getSources().get(idx_src_fixed).getSpimSource();

            Source<?> [] mvSrcs = new Source[1];
            mvSrcs[0]=movingSrc;

            Source<?> [] fxSrcs = new Source[1];
            fxSrcs[0]=fixedSrc;


            BigWarp.BigWarpData<?> bwd = BigWarpInit.createBigWarpData(mvSrcs,fxSrcs, new String[]{"MOVING", "SRC"});

            try {
                BigWarp<?> bw = new BigWarp(bwd, "Big Warp",  null);
                bw.getViewerFrameP().setVisible(true);
                bw.getViewerFrameQ().setVisible(true);
            } catch (SpimDataException e) {
                e.printStackTrace();
            }
        /*} catch (IOException e) {
            e.printStackTrace();
        }*/

    }

}
