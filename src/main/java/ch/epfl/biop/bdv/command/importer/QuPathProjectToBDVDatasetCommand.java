package ch.epfl.biop.bdv.command.importer;

import ch.epfl.biop.bdv.bioformats.BioFormatsMetaDataHelper;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvOpener;
import ch.epfl.biop.bdv.bioformats.command.BioformatsBigdataviewerBridgeDatasetCommand;
import ch.epfl.biop.bdv.bioformats.export.spimdata.BioFormatsConvertFilesToSpimData;
import ch.epfl.biop.bdv.bioformats.imageloader.FileIndex;
import ch.epfl.biop.bdv.bioformats.imageloader.SeriesNumber;
import ch.epfl.biop.bdv.command.exporter.BdvViewToImagePlusExportCommand;
import ch.epfl.biop.spimdata.qupath.MinimalQuPathProject;
import ch.epfl.biop.spimdata.qupath.ProjectIO;
import ch.epfl.biop.spimdata.qupath.QuPathEntryEntity;
import ch.epfl.biop.spimdata.qupath.QuPathToSpimData;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import net.imglib2.realtransform.AffineTransform3D;
import ome.units.quantity.Length;
import ome.units.unit.Unit;
import org.apache.commons.io.FilenameUtils;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Parameter(type = ItemIO.OUTPUT)
    AbstractSpimData spimData;

    @Parameter(label = "Dataset name (leave empty to name it like the QuPath project)", persist = false)
    public String datasetname = ""; // Cheat to allow dataset renaming

    @Override
    public void run() {

        try {
            spimData = (new QuPathToSpimData()).getSpimDataInstance(
                    quPathProject.toURI(),
                    getOpener("")
                    );
            if (datasetname.equals("")) {
                datasetname = FilenameUtils.removeExtension(FilenameUtils.getName(quPathProject.getAbsolutePath())) + ".xml";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public BioFormatsBdvOpener getOpener(String datalocation) {
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


}
