package ch.epfl.biop.bdv.commands;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvCmdSuffix;
import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Open>XML/HDF5 (SpimData2)"+ScijavaBdvCmdSuffix)
public class OpenAsSpimData2 implements Command {

    @Parameter(label = "XML File")
    public File file;

    @Parameter(label = "Show in BigDataViewer window")
    public boolean showDataset;

    @Parameter(label = "Open in new BigDataViewer window")
    public boolean createNewWindow;

    // ItemIO.BOTH required because it can be modified in case of appending new data to BDV (-> requires INPUT), or created (-> requires OUTPUT)
    @Parameter(label = "BigDataViewer Frame", type = ItemIO.BOTH, required = false)
    public BdvHandle bdv_h;

    @Parameter(type = ItemIO.OUTPUT)
    AbstractSpimData sd;

    @Override
    public void run()
    {
        try
        {
            SpimData2 spimData = new XmlIoSpimData2("").load( file.getAbsolutePath() );
            if (showDataset) {
                BdvOptions options = BdvOptions.options();
                if (createNewWindow == false && bdv_h != null) {
                    options.addTo(bdv_h);
                }
                bdv_h = BdvFunctions.show(spimData, options).get(0).getBdvHandle(); // Returns handle from index 0
            }
            sd = spimData;

        }
        catch ( SpimDataException e )
        {
            e.printStackTrace();
            throw new RuntimeException( e );
        }
    }
}
