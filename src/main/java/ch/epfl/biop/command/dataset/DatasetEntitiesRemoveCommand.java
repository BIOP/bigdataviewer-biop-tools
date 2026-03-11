package ch.epfl.biop.command.dataset;

import ij.IJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import spimdata.SpimDataHelper;

import java.io.File;

/**
 * Extra attributes like DisplaySettings break BigStitcher because the grouping is not correct...
 *
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Dataset>Dataset - Remove Entities",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.DatasetMenu, weight = BdvPgMenus.DatasetW),
                @Menu(label = "Dataset - Remove Entities", weight = 4)
        },
        description = "Removes specified entity types from a BDV dataset for compatibility with other tools")
public class DatasetEntitiesRemoveCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Input XML File",
            description = "The BDV XML dataset file to modify",
            style = "open")
    File xmlin;

    @Parameter(label = "Output XML File",
            description = "The XML file where the modified dataset will be saved",
            style = "save")
    File xmlout;

    @Parameter(label = "Entities to Remove",
            description = "Comma-separated list of entity types to remove (e.g., 'displaysettings, fileindex')")
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
