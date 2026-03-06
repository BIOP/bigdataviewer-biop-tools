package ch.epfl.biop.command.display.bdv;

import bdv.util.BdvHandle;
import org.scijava.ItemIO;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.supplier.grid.GridBdvSupplier;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

import static sc.fiji.bdvpg.scijava.service.SourceService.getCommandName;

/**
 * Command which display sources on a grid in BigDataViewer
 */
@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"View>BDV>BDV - Create Grid BDV",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.DisplayMenu, weight = BdvPgMenus.DisplayW),
                @Menu(label = BdvPgMenus.BDVMenu, weight = BdvPgMenus.BDVW),
                @Menu(label = "BDV - Create Grid BDV", weight = 2.1)
        },
        description = "Creates a new BigDataViewer window configured for grid display")
public class BdvGridCreateCommand implements BdvPlaygroundActionCommand {

    @Parameter(type = ItemIO.OUTPUT,
            label = "Grid BDV Window",
            description = "The created BigDataViewer window with grid configuration")
    BdvHandle bdvh;

    @Override
    public void run() {
        bdvh = new GridBdvSupplier().get();
    }


}
