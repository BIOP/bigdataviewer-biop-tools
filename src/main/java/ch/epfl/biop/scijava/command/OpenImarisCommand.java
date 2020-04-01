package ch.epfl.biop.scijava.command;

import bdv.img.imaris.Imaris;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

import java.io.File;
import java.io.IOException;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"SpimDataset>Open Imaris")
public class OpenImarisCommand implements Command {

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
