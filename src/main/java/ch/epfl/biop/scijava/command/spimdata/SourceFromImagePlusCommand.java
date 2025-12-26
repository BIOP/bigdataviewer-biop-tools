package ch.epfl.biop.scijava.command.spimdata;

import ch.epfl.biop.bdv.img.imageplus.ImagePlusToSpimData;
import ij.ImagePlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Import>Make BDVDataset from current IJ1 image",
        description = "Converts an ImageJ1 ImagePlus to a BigDataViewer dataset for visualization and processing")
public class SourceFromImagePlusCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Input Image",
            description = "The ImagePlus image to convert to a BDV dataset")
    ImagePlus image;

    //@Parameter(type = ItemIO.OUTPUT) // Removed because because using it as a parameter currently prevents
    // its naming...
    // So it's like the postprocessor of the SpimData is done inside the command
    // AbstractSpimData asd;

    @Parameter
    SourceAndConverterService sac_service;

    public void run() {
        AbstractSpimData asd = ImagePlusToSpimData.getSpimData(image);
        sac_service.register(asd);
        sac_service.setSpimDataName(asd, image.getTitle());
    }

}
