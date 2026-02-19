package ch.epfl.biop.command.bdv;

import bdv.util.BdvHandle;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.supplier.grid.GridBdvSupplier;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

import static sc.fiji.bdvpg.scijava.services.SourceService.getCommandName;

/**
 * Command which display sources on a grid in BigDataViewer
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Viewers>BDV>BDV - Make Grid BDV",
        description = "Creates a new BigDataViewer window configured for grid display")
public class ShowGridBdvCommand implements BdvPlaygroundActionCommand {

    @Parameter(type = ItemIO.OUTPUT,
            label = "Grid BDV Window",
            description = "The created BigDataViewer window with grid configuration")
    BdvHandle bdvh;

    @Override
    public void run() {
        bdvh = new GridBdvSupplier().get();
    }


}
