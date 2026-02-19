package ch.epfl.biop.command.dataset;

import ch.epfl.biop.bdv.img.imageplus.ImagePlusToSpimData;
import ij.ImagePlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceService;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Import>Create BDV Dataset [Current ImagePlus]",
        description = "Converts an ImageJ1 ImagePlus to a BigDataViewer dataset for visualization and processing")
public class DatasetFromImagePlusCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Input Image",
            description = "The ImagePlus image to convert to a BDV dataset")
    ImagePlus image;

    //@Parameter(type = ItemIO.OUTPUT) // Removed because because using it as a parameter currently prevents
    // its naming...
    // So it's like the postprocessor of the SpimData is done inside the command
    // AbstractSpimData asd;

    @Parameter
    SourceService source_service;

    public void run() {
        AbstractSpimData asd = ImagePlusToSpimData.getSpimData(image);
        source_service.register(asd);
        source_service.setDatasetName(asd, image.getTitle());
    }

}
