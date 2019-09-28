package ch.epfl.biop.bdv.multisourcealign;

import bdv.VolatileSpimSource;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.util.ColorRGB;

import java.awt.Color;
import java.util.*;
import java.io.File;
import java.util.List;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Registration>Open Scans And Append In Z")
public class OpenSlideAndArrange implements Command {

    @Parameter
    double zStartingLocation = 0;

    @Parameter
    double zSliceSize = 0.01;

    @Parameter
    ColorRGB c;

    @Parameter
    double valMin;

    @Parameter
    double valMax;

    @Parameter
    int indexSourceStart = 2;

    @Parameter
    int nChannels = 3;

    @Parameter
    int timePoint =0;

    @Parameter
    int skipSourceIndexEnd = 1;

    @Parameter
    File file;

    @Parameter(label = "Open in new BigDataViewer window")
    public boolean createNewWindow;

    // ItemIO.BOTH required because it can be modified in case of appending new data to BDV (-> requires INPUT), or created (-> requires OUTPUT)
    @Parameter(label = "BigDataViewer Frame", type = ItemIO.BOTH, required = false)
    public BdvHandle bdv_h;

    @Override
    public void run() {
        final SpimDataMinimal spimData;
        try {
            spimData = new XmlIoSpimDataMinimal().load( file.getAbsolutePath() );
            BdvOptions options = BdvOptions.options();
            if (createNewWindow == false && bdv_h!=null) {
                options.addTo(bdv_h);
            }

            int nViewSetups = spimData.getSequenceDescription().getViewSetupsOrdered().size();

            for (int iViewSetup = indexSourceStart; iViewSetup<(nViewSetups-skipSourceIndexEnd);iViewSetup+=nChannels) {

                final VolatileSpimSource vs = new VolatileSpimSource<>( spimData, iViewSetup, "" );

                int sx = (int) vs.getSource(timePoint,0).dimension(0);

                int sy = (int) vs.getSource(timePoint,0).dimension(1);

                final TransformedSource tvs = new TransformedSource<>( vs );

                AffineTransform3D at3D = spimData.getViewRegistrations().getViewRegistration(timePoint,iViewSetup).getModel();

                AffineTransform3D at3DEdited;
                at3DEdited = at3D.inverse();
                at3DEdited.translate(-sx/2, -sy/2,0);
                at3DEdited.scale(at3D.get(0,0),at3D.get(1,1),zSliceSize);
                at3DEdited.translate(0,0,((double)(iViewSetup-indexSourceStart)/(double)nChannels)*zSliceSize);
                tvs.setFixedTransform(at3DEdited);

                List<SourceAndConverter> list = BdvFunctions.show( tvs, options ).getSources();//.get(0).getBdvHandle(); // Returns handle from index 0

            }

        } catch (SpimDataException e) {
            e.printStackTrace();
        }


    }
}
