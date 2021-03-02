package ch.epfl.biop.bdv.command.importer;

import bdv.img.imaris.Imaris;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.io.File;
import java.io.IOException;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDVDataset>Open Imaris")
public class OpenImarisCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Imaris File")
    public File file;

    @Parameter(type = ItemIO.OUTPUT)
    AbstractSpimData spimData;

    @Override
    public void run() {
        try
        {
            spimData = Imaris.openIms( file.getAbsolutePath() );
        }
        catch ( final IOException e )
        {
            throw new RuntimeException( e );
        }
    }
}
