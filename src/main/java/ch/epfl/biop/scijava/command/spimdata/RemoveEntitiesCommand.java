package ch.epfl.biop.scijava.command.spimdata;

import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.ViewSetupAttributes;
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
@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Edit>Remove Entities from BDVDataset")
public class RemoveEntitiesCommand implements Command {

    @Parameter(label="Xml Bdv Dataset input", style = "open")
    File xmlin;

    @Parameter(label="Xml Bdv Dataset output", style = "save")
    File xmlout;

    @Parameter
    String entitiestoremove = "displaysettings, fileindex";

    @Override
    public void run() {

        if (xmlout.exists()) {
            IJ.error("The output file already exist! Skipping execution");
            return;
        }

        try {
            String[] entities = entitiestoremove.split(",");
            AbstractSpimData<?> asd = new XmlIoSpimData().load(xmlin.getAbsolutePath());
            SpimDataHelper.removeEntities(asd, entities);
            new XmlIoSpimData().save((SpimData) asd, xmlout.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
