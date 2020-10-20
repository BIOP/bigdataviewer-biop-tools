package ch.epfl.biop.scijava.command;

import ch.epfl.biop.spimdata.imageplus.SpimDataFromImagePlusGetter;
import ij.ImagePlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Import>Get Sources From ImagePlus")
public class SourceFromImagePlusCommand implements Command {

    @Parameter
    ImagePlus imagePlus;

    //@Parameter(type = ItemIO.OUTPUT) // Removed because because using it as a parameter currently prevents
    // its naming...
    // So it's like the postprocessor of the SpimData is done inside the command
    AbstractSpimData asd;

    @Parameter
    SourceAndConverterService sac_service;

    public void run() {
        asd = (new SpimDataFromImagePlusGetter()).apply(imagePlus);
        sac_service.register(asd);
        sac_service.setSpimDataName(asd, imagePlus.getTitle());
    }

}
