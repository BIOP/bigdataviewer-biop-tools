package ch.epfl.biop.scijava.command.spimdata;

import ch.epfl.biop.bdv.img.OpenersToSpimData;
import ch.epfl.biop.bdv.img.bioformats.BioFormatsHelper;
import ch.epfl.biop.bdv.img.entity.ImageName;
import ch.epfl.biop.bdv.img.opener.OpenerSettings;
import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import spimdata.SpimDataHelper;
import spimdata.util.Displaysettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Edit>Make CZI Dataset for BigStitcher")
public class CreateCZIDatasetCommand implements Command {

    @Parameter(style = "open")
    File czi_file;

    @Parameter
    Context ctx;

    @Parameter
    Boolean erase_if_file_already_exists = false;

    @Parameter(type = ItemIO.BOTH, label = "Output file (xml)", style = "save")
    File xml_out = null;

    @Override
    public void run() {

        if (xml_out.exists()) {
            if (xml_out.isFile()) {
                if (erase_if_file_already_exists) {
                    boolean isDeleted = xml_out.delete();
                    if (!isDeleted) {
                        IJ.error("ommand aborted: the output file could not be deleted.");
                        return;
                    }
                } else {
                    IJ.error("Command aborted: the output file already exists!");
                    return;
                }
            } else {
                IJ.error("Command aborted: the output path is a folder, it won't be deleted.");
                return;
            }
        }

        // We need to:
        // make a spimdata
        // rescale it
        // get rid of extra attributes
        // save the xml at the same place as the file
        String bfOptions = "--bfOptions zeissczi.autostitch=false";
        List<OpenerSettings> openerSettings = new ArrayList<>();
        int nSeries = BioFormatsHelper.getNSeries(czi_file, bfOptions);
        for (int i = 0; i < nSeries; i++) {
            openerSettings.add(
                    OpenerSettings.BioFormats()
                            .location(czi_file)
                            .setSerie(i)
                            .micrometer()
                            .splitRGBChannels(true)
                            .cornerPositionConvention()
                            .addOptions(bfOptions)
                            .context(ctx));
        }
        AbstractSpimData<?> asd = OpenersToSpimData.getSpimData(openerSettings);

        // Remove display settings attributes because this causes issues with BigStitcher
        SpimDataHelper.removeEntities(asd, Displaysettings.class, ImageName.class);

        double pixSizeXYMicrometer = asd.getViewRegistrations().getViewRegistration(0,0).getModel().get(0,0);

        double scalingForBigStitcher = 1.0 / pixSizeXYMicrometer;

        // Scaling such as size of one pixel = 1
        SpimDataHelper.scale(asd, "BigStitcher Scaling", scalingForBigStitcher);

        asd.setBasePath(new File(xml_out.getAbsolutePath()).getParentFile());

        try {
            new XmlIoSpimData().save((SpimData) asd, xml_out.getAbsolutePath());
        } catch (SpimDataException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }
}
