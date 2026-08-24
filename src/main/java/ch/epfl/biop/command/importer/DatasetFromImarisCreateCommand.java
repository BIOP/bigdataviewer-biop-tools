package ch.epfl.biop.command.importer;

import bdv.img.imaris.Imaris;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

import java.io.File;
import java.io.IOException;

// Legacy command, kept for backward compatibility with existing scripts.
// Declared as a plain Command rather than a BdvPlaygroundActionCommand: it has no menu
// path, and the BDV Playground SourceService cannot register a menu-less action.
@Plugin(type = Command.class,
        //menuPath = BdvPgMenus.RootMenu+"Import>Dataset - Create [Imaris]",
        description = "Opens an Imaris .ims file as a BigDataViewer dataset")
public class DatasetFromImarisCreateCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Imaris File",
            description = "Path to the Imaris .ims file to open")
    public File file;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The opened Imaris dataset")
    AbstractSpimData spimdata;

    @Override
    public void run() {
        try
        {
            spimdata = Imaris.openIms( file.getAbsolutePath() );
        }
        catch ( final IOException e )
        {
            throw new RuntimeException( e );
        }
    }
}
