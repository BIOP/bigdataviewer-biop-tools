package ch.epfl.biop.scijava.command.spimdata;

import ch.epfl.biop.bdv.img.bioformats.entity.SeriesIndex;
import ch.epfl.biop.bdv.img.entity.ImageName;
import ch.epfl.biop.bdv.img.legacy.bioformats.BioFormatsSetupLoader;
import ch.epfl.biop.bdv.img.legacy.bioformats.entity.FileIndex;
import ch.epfl.biop.bdv.img.legacy.bioformats.entity.SeriesNumber;
import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import spimdata.SpimDataHelper;
import spimdata.util.Displaysettings;

import java.io.File;

/**
 * Extra attributes like DisplaySettings break BigStitcher because the grouping is not correct...
 *
 */
@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Edit>Make BDVDataset BigStitcher Compatible")
public class DatasetToBigStitcherDatasetCommand implements Command {

    @Parameter(label="Xml Bdv Dataset input", style = "open")
    File xmlin;

    @Parameter(label="View setup reference for rescaling, -1 to list all voxel dimensions and pick the first", persist = false)
    int viewsetupreference = -1;

    @Parameter(label="Xml Bdv Dataset output", style = "save")
    File xmlout;

    @Override
    public void run() {
        if (xmlout.exists()) {
            IJ.error("The output file already exist! Skipping execution");
            return;
        }

        try {

            AbstractSpimData<?> asd = new XmlIoSpimData().load(xmlin.getAbsolutePath());

            // We assume all pixel sizes are equal
            BasicImgLoader imageLoader =  asd.getSequenceDescription().getImgLoader();
            double scalingForBigStitcher = 1;
            int nSetups = asd.getSequenceDescription().getViewSetupsOrdered().size();
            if (viewsetupreference ==-1) {
                for (int i = 0; i < nSetups; i++) {
                    MultiResolutionSetupImgLoader setupLoader = (MultiResolutionSetupImgLoader) imageLoader.getSetupImgLoader(i);
                    BioFormatsSetupLoader l;
                    VoxelDimensions voxelDimensions = setupLoader.getVoxelSize(0);
                    voxelDimensions.dimension(0);
                    IJ.log("VS["+i+"] = "+voxelDimensions);
                }
                viewsetupreference = 0;
            }
            MultiResolutionSetupImgLoader setupLoader = (MultiResolutionSetupImgLoader) imageLoader.getSetupImgLoader(viewsetupreference);
            scalingForBigStitcher = 1./setupLoader.getVoxelSize(0).dimension(0);

            // Remove display settings attributes because this causes issues with BigStitcher
            SpimDataHelper.removeEntities(asd, Displaysettings.class, FileIndex.class, SeriesIndex.class, SeriesNumber.class, ImageName.class);

            // Scaling such as size of one pixel = 1
            SpimDataHelper.scale(asd, "BigStitcher Scaling", scalingForBigStitcher);


            asd.setBasePath(new File(xmlout.getAbsolutePath()).getParentFile()); //TODO TOFIX
            new XmlIoSpimData().save((SpimData) asd, xmlout.getAbsolutePath());

            IJ.log("- Done! Dataset created - "+xmlout.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
