package alpha;

import bdv.util.BdvHandle;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import fused.TestHelper;
import net.imagej.ImageJ;
import net.imglib2.Volatile;
import net.imglib2.converter.Converter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import bdv.util.converters.RealARGBColorConverter;
import sc.fiji.bdvpg.services.SourceAndConverterServiceLoader;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

public class AlphaDemoWithCustomConverter {

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

        Source<?> non_volatile_source = sources[5].getSpimSource();

        Source<?> volatile_source = sources[5].asVolatile().getSpimSource();

        Converter converter = createConverterRealType((RealType) non_volatile_source.getType());
        SourceAndConverter vsac = new SourceAndConverter(volatile_source, converter);
        SourceAndConverter sac = new SourceAndConverter(non_volatile_source, converter, vsac);

        SourceAndConverterServices
                .getBdvDisplayService()
                .show(bdv, sac);

        // Zoom out
        AffineTransform3D view = new AffineTransform3D();
        bdv.getViewerPanel().state().getViewerTransform(view);
        view.scale(0.005);
        bdv.getViewerPanel().state().setViewerTransform(view);
        bdv.getSplitPanel().setCollapsed(false);

        /*
        List<SourceGroup> groups = bdv.getViewerPanel().state().getGroups();

        for (int i=0;i<2;i++) {
            SourceGroup group = groups.get(i);
            bdv.getViewerPanel().state().addSourceToGroup(sources[i], group);
        }*/

    }

    @Test
    public void demoRunOk() {
        main(new String[]{""});
    }

    @After
    public void closeFiji() {
        TestHelper.closeFijiAndBdvs(ij);
    }

    /**
     * Creates ARGB converter from a RealTyped sourceandconverter.
     * Supports Volatile RealTyped or non volatile
     * @param <T> realtype class
     * @return a suited converter
     */
    public static< T extends RealType< T >> Converter createConverterRealType(final T type ) {
        final double typeMin = Math.max( 0, Math.min( type.getMinValue(), 65535 ) );
        final double typeMax = Math.max( 0, Math.min( type.getMaxValue(), 65535 ) );
        final RealARGBColorConverter< T > converter ;
        if ( type instanceof Volatile)
            converter = new RealARGBColorConverter.Imp0<>( typeMin, typeMax );
        else
            converter = new RealARGBColorConverter.Imp1<>( typeMin, typeMax );
        converter.setColor( new ARGBType( 0xffffffff ) );

        ((RealARGBColorConverter)converter).getValueToColor().put( 0D, ARGBType.rgba( 0, 0, 0, 0) );
        return converter;
    }

}
