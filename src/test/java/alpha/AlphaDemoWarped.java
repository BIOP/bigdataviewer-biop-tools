package alpha;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import fused.TestHelper;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import sc.fiji.bdvpg.services.SourceAndConverterServiceLoader;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

public class AlphaDemoWarped {

    static ImageJ ij;

    public static void main( String[] args )
    {
        ij = new ImageJ();
        ij.ui().showUI();
        //new SourceAndConverterServiceLoader("src/test/resources/bdvplaygroundstate.json", "src/test/resources/", ij.context(), false).run();
        new SourceAndConverterServiceLoader("src/test/resources/bdvplaygroundstate.json", "src/test/resources/", ij.context(), false).run();

        IBdvSupplier bdvSupplier = new AlphaBdvSupplier(new AlphaSerializableBdvOptions());

        SourceAndConverterServices.getBdvDisplayService().setDefaultBdvSupplier(bdvSupplier);

        BdvHandle bdv = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        SourceAndConverter<?>[] sources = SourceAndConverterServices.getSourceAndConverterService().getSourceAndConverters().toArray(new SourceAndConverter[0]);

        SourceAndConverterServices
                .getBdvDisplayService()
                .show(bdv, sources[6]);

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
