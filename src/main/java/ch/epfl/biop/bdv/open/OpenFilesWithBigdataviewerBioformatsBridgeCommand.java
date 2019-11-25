package ch.epfl.biop.bdv.open;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvStackSource;
import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.BioformatsBdvDisplayHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.cache.GuavaWeakCacheService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, menuPath = "BDV_SciJava>SpimDataset>Open>SpimDataset [BioFormats Bdv Bridge + SHOW]")
public class OpenFilesWithBigdataviewerBioformatsBridgeCommand extends BioformatsBigdataviewerBridgeDatasetCommand {

    @Parameter
    File[] files;

    @Parameter(required = false)
    boolean setColor = true;

    @Parameter(required = false)
    boolean setGrouping = true;

    @Parameter(required = false)
    double minDisplay = 0;

    @Parameter(required = false)
    double maxDisplay = 255;

    @Parameter(type = ItemIO.OUTPUT)
    public BdvHandle bdv_h;

    @Parameter(type = ItemIO.OUTPUT)
    AbstractSpimData spimData;

    @Parameter
    GuavaWeakCacheService cs;

    @Parameter
    boolean verbose;


    public void run() {

        if (verbose) {
            BioFormatsMetaDataHelper.log = (s) -> System.out.println(s);
        }

        List<BioFormatsBdvOpener> openers = new ArrayList<>();
        for (File f:files) {
            openers.add(getOpener(f));
        }
        spimData = BioFormatsConvertFilesToSpimData.getSpimData(openers);
        List<BdvStackSource<?>> lbss = BdvFunctions.show(spimData);
        bdv_h = lbss.get(0).getBdvHandle();
        BioformatsBdvDisplayHelper.autosetColorsAngGrouping(lbss, spimData, setColor, minDisplay, maxDisplay, setGrouping);
        cs.put(spimData, lbss);

        if (verbose) {
            BioFormatsMetaDataHelper.log = (s) -> {};
        }
    }

}
