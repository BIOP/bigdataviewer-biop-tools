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
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import ch.epfl.biop.bdv.commands.BDVSlicesToImgPlus;
import mpicbg.spim.data.SpimDataException;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;


@Plugin(type = Command.class,menuPath = "Plugins>BigDataViewer>SciJava>BigWarp (SciJava)")
public class InitBigWarpWithBdvSources implements Command {

    private static final Logger LOGGER = Logger.getLogger( InitBigWarpWithBdvSources.class.getName() );

    @Parameter
    BdvHandle bdv_h;

    @Parameter(label="Moving source indexes ('2,3-5'), starts at 0")
    String idx_src_moving;

    @Parameter(label="Fixed source indexes ('2,3-5'), starts at 0")
    String idx_src_fixed;

    @Override
    public void run() {

            Source<?> [] mvSrcs  =
                    commaSeparatedListToArray(idx_src_moving)
                    .stream()
                    .map(idx -> bdv_h.getViewerPanel().getState().getSources().get(idx).getSpimSource())
                    .collect(Collectors.toList())
                    .toArray(new Source<?>[]{});

            Source<?> [] fxSrcs  =
                    commaSeparatedListToArray(idx_src_fixed)
                    .stream()
                    .map(idx -> bdv_h.getViewerPanel().getState().getSources().get(idx).getSpimSource())
                    .collect(Collectors.toList())
                    .toArray(new Source<?>[]{});

            String[] dummyNames = new String[mvSrcs.length+fxSrcs.length];

            for (int i=0;i<dummyNames.length;i++) {
                dummyNames[i]="Src "+i;
            }

            BigWarp.BigWarpData<?> bwd = BigWarpInit.createBigWarpData(mvSrcs,fxSrcs, dummyNames);

            try {
                BigWarp<?> bw = new BigWarp(bwd, "Big Warp",  null);
                bw.getViewerFrameP().setVisible(true);
                bw.getViewerFrameQ().setVisible(true);
            } catch (SpimDataException e) {
                e.printStackTrace();
            }

    }

    /**
     * Convert a comma separated list of indexes into an arraylist of integer
     *
     * For instance 1,2,5-7,10-12,14 returns an ArrayList containing
     * [1,2,5,6,7,10,11,12,14]
     *
     * Invalid format are ignored and an error message is displayed
     *
     * @param expression
     * @return list of indexes in ArrayList
     */

    static public ArrayList<Integer> commaSeparatedListToArray(String expression) {
        String[] splitIndexes = expression.split(",");
        ArrayList<java.lang.Integer> arrayOfIndexes = new ArrayList<>();
        for (String str : splitIndexes) {
            str.trim();
            if (str.contains("-")) {
                // Array of source, like 2-5 = 2,3,4,5
                String[] boundIndex = str.split("-");
                if (boundIndex.length==2) {
                    try {
                        int binf = java.lang.Integer.valueOf(boundIndex[0].trim());
                        int bsup = java.lang.Integer.valueOf(boundIndex[1].trim());
                        for (int index = binf; index <= bsup; index++) {
                            arrayOfIndexes.add(index);
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Number format problem with expression:"+str+" - Expression ignored");
                    }
                } else {
                    LOGGER.warning("Cannot parse expression "+str+" to pattern 'begin-end' (2-5) for instance, omitted");
                }
            } else {
                // Single source
                try {
                    int index = java.lang.Integer.valueOf(str.trim());
                    arrayOfIndexes.add(index);
                } catch (NumberFormatException e) {
                    LOGGER.warning("Number format problem with expression:"+str+" - Expression ignored");
                }
            }
        }
        return arrayOfIndexes;
    }

}
