package ch.epfl.biop.scijava.command.spimdata;

import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.spimdata.qupath.GuiParams;
import ch.epfl.biop.spimdata.qupath.QuPathToSpimData;
import ij.IJ;
import mpicbg.spim.data.generic.AbstractSpimData;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.apache.commons.io.FilenameUtils;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
                    //getOpener("")
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

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

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


    /*public BioFormatsBdvOpener getOpener(String datalocation) {
        Unit bfUnit = BioFormatsMetaDataHelper.getUnitFromString(this.unit);
        Length positionReferenceFrameLength = new Length(this.refframesizeinunitlocation, bfUnit);
        Length voxSizeReferenceFrameLength = new Length(this.refframesizeinunitvoxsize, bfUnit);
        BioFormatsBdvOpener opener = BioFormatsBdvOpener.getOpener()
                .location(datalocation).unit(this.unit)
                //.auto()
                .ignoreMetadata();
        if (!this.switchzandc.equals("AUTO")) {
            opener = opener.switchZandC(this.switchzandc.equals("TRUE"));
        }

        if (!this.usebioformatscacheblocksize) {
            opener = opener.cacheBlockSize(this.cachesizex, this.cachesizey, this.cachesizez);
        }

        if (!this.positioniscenter.equals("AUTO")) {
            if (this.positioniscenter.equals("TRUE")) {
                opener = opener.centerPositionConvention();
            } else {
                opener = opener.cornerPositionConvention();
            }
        }

        if (!this.flippositionx.equals("AUTO") && this.flippositionx.equals("TRUE")) {
            opener = opener.flipPositionX();
        }

        if (!this.flippositiony.equals("AUTO") && this.flippositiony.equals("TRUE")) {
            opener = opener.flipPositionY();
        }

        if (!this.flippositionz.equals("AUTO") && this.flippositionz.equals("TRUE")) {
            opener = opener.flipPositionZ();
        }

        opener = opener.unit(this.unit);
        opener = opener.positionReferenceFrameLength(positionReferenceFrameLength);
        opener = opener.voxSizeReferenceFrameLength(voxSizeReferenceFrameLength);
        if (this.splitrgbchannels) {
            opener = opener.splitRGBChannels();
        }

        return opener;
    }
*/

}
