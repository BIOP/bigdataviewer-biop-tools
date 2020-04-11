package ch.epfl.biop.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.importer.SourcesFromImagePlusGetter;
import ch.epfl.biop.spimdata.SpimDataFromImagePlusGetter;
import ij.ImagePlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Import>Get Sources From ImagePlus")
public class SourceFromImagePlusCommand implements Command {

    @Parameter
    ImagePlus imagePlus;

    @Parameter(type = ItemIO.OUTPUT)
    AbstractSpimData asd;
    //SourceAndConverter[] sacs;

    public void run() {
        /*SourcesFromImagePlusGetter getter = new SourcesFromImagePlusGetter(imagePlus);
        getter.run();
        sacs = getter.getSources().toArray(new SourceAndConverter[getter.getSources().size()]);*/
        asd = (new SpimDataFromImagePlusGetter()).apply(imagePlus);

    }

}
