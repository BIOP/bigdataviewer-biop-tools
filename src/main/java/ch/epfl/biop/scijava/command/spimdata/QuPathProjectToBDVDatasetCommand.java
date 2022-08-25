package ch.epfl.biop.scijava.command.spimdata;

import ch.epfl.biop.bdv.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.spimdata.qupath.GuiParams;
import ch.epfl.biop.spimdata.qupath.QuPathToSpimData;

import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

import java.io.File;


/**
 * Warning : a qupath project may have its source reordered and or removed :
 * - not all entries will be present in the qupath project
 * Limitations : only images
 */

@Plugin(type = Command.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Open [QuPath Project]"
        )
public class QuPathProjectToBDVDatasetCommand extends BioformatsBigdataviewerBridgeDatasetCommand {

    private static Logger logger = LoggerFactory.getLogger(QuPathProjectToBDVDatasetCommand.class);

    @Parameter
    File quPathProject;

    @Parameter(label = "Dataset name (leave empty to name it like the QuPath project)", persist = false)
    public String datasetname = ""; // Cheat to allow dataset renaming

    @Parameter(type = ItemIO.OUTPUT)
    AbstractSpimData spimData;


    @Override
    public void run() {

        try {
                spimData = (new QuPathToSpimData()).getSpimDataInstance(
                        quPathProject.toURI(),
                        getGuiParams()
                );
                if (datasetname.equals("")) {
                    datasetname = quPathProject.getParentFile().getName();//FilenameUtils.removeExtension(FilenameUtils.getName(quPathProject.getAbsolutePath())) + ".xml";
                }

                // Directly registers it to prevent memory leak...
            /*SourceAndConverterServices
                    .getSourceAndConverterService()
                    .register(spimData);
            SourceAndConverterServices
                    .getSourceAndConverterService()
                    .setSpimDataName(spimData, datasetname);*/

                // End of session
               // Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    //fail
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Get from the interface all parameters given by the user
     * @return
     */
    public GuiParams getGuiParams(){
        return (new GuiParams()).setUnit(this.unit)
                                .setCachesizex(this.cachesizex)
                                .setCachesizey(this.cachesizey)
                                .setCachesizez(this.cachesizez)
                                .setFlippositionx(this.flippositionx)
                                .setFlippositiony(this.flippositiony)
                                .setFlippositionz(this.flippositionz)
                                .setNumberofblockskeptinmemory(this.numberofblockskeptinmemory)
                                .setPositioniscenter(this.positioniscenter)
                                .setPositionReferenceFrameLength(this.refframesizeinunitvoxsize)
                                .setSplitChannels(this.splitrgbchannels)
                                .setSwitchzandc(this.switchzandc)
                                .setUsebioformatscacheblocksize(this.usebioformatscacheblocksize)
                                .setVoxSizeReferenceFrameLength(this.refframesizeinunitvoxsize);
    }
}
