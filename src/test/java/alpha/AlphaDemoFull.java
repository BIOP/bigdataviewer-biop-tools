package alpha;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import fused.TestHelper;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.viewers.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import sc.fiji.bdvpg.services.SourceServiceLoader;
import sc.fiji.bdvpg.services.SourceServices;

public class AlphaDemoFull {

    static ImageJ ij;

    public static void main( String[] args )
    {
        ij = new ImageJ();
        ij.ui().showUI();
        //new SourceServiceLoader("src/test/resources/bdvplaygroundstate.json", "src/test/resources/", ij.context(), false).run();
        new SourceServiceLoader("src/test/resources/bdvplaygroundstate.json", "src/test/resources/", ij.context(), false).run();

        IBdvSupplier bdvSupplier = new AlphaBdvSupplier(new AlphaSerializableBdvOptions());

        SourceServices.getBdvDisplayService().setDefaultBdvSupplier(bdvSupplier);

        BdvHandle bdv = SourceServices.getBdvDisplayService().getNewBdv();

        SourceAndConverter<?>[] sources = SourceServices.getSourceService().getSources().toArray(new SourceAndConverter[0]);

        SourceServices
                .getBdvDisplayService()
                .show(bdv, sources);

        // Zoom out
        AffineTransform3D view = new AffineTransform3D();
        bdv.getViewerPanel().state().getViewerTransform(view);
        view.scale(0.005);
        bdv.getViewerPanel().state().setViewerTransform(view);
        bdv.getSplitPanel().setCollapsed(false);

    }

    @Test
    public void demoRunOk() {
        main(new String[]{""});
    }

    @After
    public void closeFiji() {
        TestHelper.closeFijiAndBdvs(ij);
    }



}
