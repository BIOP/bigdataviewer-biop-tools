package fused;

import bdv.util.source.fused.AlphaFusedResampledSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.EmptyMultiResolutionSourceAndConverterCreator;
import ch.epfl.biop.sourceandconverter.SourceFuserAndResampler;
import ij.IJ;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import javax.swing.tree.TreePath;
import java.util.List;

public class FusedRamChallenge {

    static ImageJ ij;

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        DebugTools.enableLogging ("OFF");
        ij = new ImageJ();
        ij.ui().showUI();

        double oX = 0;
        double oY = 0;
        double oZ = 0;

        double sx = 24.0;
        double sy = 16;
        double sz = 0.01;

        int nPx = 3000*24;
        int nPy = (int) (((double)nPx)*sy/sx);
        int nPz = 1;

        AffineTransform3D transform = new AffineTransform3D();
        transform.scale(sx/nPx,sy/nPy,sz/nPz);
        transform.translate(oX,oY,oZ);

        SourceAndConverter model = new EmptyMultiResolutionSourceAndConverterCreator("model", transform, nPx, nPy, nPz, 1, 2, 2, 2, 1).get();

        SourceAndConverterServices.getSourceAndConverterService().register(model);

        IJ.log(" Loading dataset ");
        AbstractSpimData asd = new SpimDataFromXmlImporter("C:\\Users\\nicol\\Downloads\\CompositeTiles\\CompositeTiles\\tiles.xml").get();

        IJ.log(" Dataset loaded ");

        String sourcesPath = "tiles.xml>Channel>0";

        SourceAndConverterService sac_service = ij.get(SourceAndConverterService.class);

        TreePath tp =
                sac_service
                .getUI()
                .getTreePathFromString(sourcesPath);

        //List<SourceAndConverter> sources = sac_service.getUI().getSourceAndConvertersFromTreePath(tp);

        //IJ.log("Now fusing "+sources.size()+" sources");

        //SourceAndConverter fused = new SourceFuserAndResampler(sources, AlphaFusedResampledSource.AVERAGE, model, "Fused_ch0", true, true, false, 0, 1024,1024,1,10).get();

        //sac_service.register(fused);
    }
}
