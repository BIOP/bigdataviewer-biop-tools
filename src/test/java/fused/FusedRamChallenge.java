package fused;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.EmptyMultiResolutionSourceCreator;
import ij.IJ;
import loci.common.DebugTools;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.scijava.services.SourceService;
import sc.fiji.bdvpg.services.SourceServices;
import sc.fiji.bdvpg.dataset.importer.SpimDataFromXmlImporter;

import javax.swing.tree.TreePath;

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

        SourceAndConverter model = new EmptyMultiResolutionSourceCreator("model", transform, nPx, nPy, nPz, 1, 2, 2, 2, 1).get();

        SourceServices.getSourceService().register(model);

        IJ.log(" Loading dataset ");
        AbstractSpimData asd = new SpimDataFromXmlImporter("C:\\Users\\nicol\\Downloads\\CompositeTiles\\CompositeTiles\\tiles.xml").get();

        IJ.log(" Dataset loaded ");

        String sourcesPath = "tiles.xml>Channel>0";

        SourceService source_service = ij.get(SourceService.class);

        TreePath tp =
                source_service
                .tree()
                .getTreePathFromString(sourcesPath);

        //List<SourceAndConverter> sources = source_service.getUI().getSourceAndConvertersFromTreePath(tp);

        //IJ.log("Now fusing "+sources.size()+" sources");

        //SourceAndConverter fused = new SourceFuserAndResampler(sources, AlphaFusedResampledSource.AVERAGE, model, "Fused_ch0", true, true, false, 0, 1024,1024,1,10).get();

        //source_service.register(fused);
    }
}
