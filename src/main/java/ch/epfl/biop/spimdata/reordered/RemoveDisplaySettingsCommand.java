package ch.epfl.biop.spimdata.reordered;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.ViewSetupAttributes;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import spimdata.util.Displaysettings;

import java.io.File;

/**
 * Extra attributes like DisplaySettings break BigStitcher because the grouping is not correct...
 *
 */
@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Remove Display Settings from BDVDataset")
public class RemoveDisplaySettingsCommand implements Command {

    @Parameter(label="Xml Bdv Dataset input", style = "open")
    File xmlin;

    @Parameter(label="Xml Bdv Dataset output", style = "save")
    File xmlout;

    @Override
    public void run() {
        try {

            AbstractSpimData<?> asd = new XmlIoSpimData().load(xmlin.getAbsolutePath());
            asd.getSequenceDescription().getViewSetups().forEach((id, vs) -> {
                if (vs.getAttribute(Displaysettings.class)!=null) {
                    vs.getAttributes().remove(ViewSetupAttributes.getNameForClass( Displaysettings.class ));
                }
            });

            new XmlIoSpimData().save((SpimData) asd, xmlout.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
