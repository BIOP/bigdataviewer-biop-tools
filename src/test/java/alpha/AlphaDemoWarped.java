package alpha;

import bdv.util.BdvHandle;
import bdv.util.DefaultInterpolators;
import bdv.util.source.alpha.AlphaSourceHelper;
import bdv.util.source.alpha.AlphaSourceRAI;
import bdv.util.source.alpha.IAlphaSource;
import bdv.viewer.Interpolation;
import bdv.viewer.SourceAndConverter;
import fused.TestHelper;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imagej.ImageJ;
import net.imglib2.*;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import org.junit.After;
import org.junit.Test;
import sc.fiji.bdvpg.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import sc.fiji.bdvpg.services.SourceAndConverterServiceLoader;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.function.Supplier;

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

        SourceAndConverter warpedSource = sources[2];

        IAlphaSource alpha = getAlphaSource(new FinalInterval(new long[]{200,250,10}), new AffineTransform3D());

        AlphaSourceHelper.setAlphaSource(warpedSource, alpha);

        SourceAndConverterServices
                .getBdvDisplayService()
                .show(bdv, warpedSource);

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


    protected static final DefaultInterpolators< FloatType > interpolators = new DefaultInterpolators<>();

    public static IAlphaSource getAlphaSource(FinalInterval finalInterval, AffineTransform3D location) {

        FinalVoxelDimensions voxD = new FinalVoxelDimensions("px", 1, 1, 1);
        return new IAlphaSource() {
            @Override
            public boolean intersectBox(AffineTransform3D affineTransform, Interval cell, int timepoint) {
                // Let's try a simplebox computation and see if there are intersections.
                AlphaSourceRAI.Box3D box_cell = new AlphaSourceRAI.Box3D(affineTransform, cell);
                AffineTransform3D affineTransform3D = new AffineTransform3D();
                getSourceTransform(timepoint,0,affineTransform3D);
                AlphaSourceRAI.Box3D box_this = new AlphaSourceRAI.Box3D(affineTransform3D, this.getSource(timepoint,0));
                return box_this.intersects(box_cell);
            }

            @Override
            public boolean isPresent(int t) {
                return t==0;
            }

            @Override
            public RandomAccessibleInterval<FloatType> getSource(int t, int level) {
                final RandomAccessible< FloatType > randomAccessible =
                        new FunctionRandomAccessible<>( 3, () -> (loc, out) -> out.setReal( 1f ), FloatType::new );
                return Views.interval(randomAccessible, finalInterval);
            }

            @Override
            public RealRandomAccessible<FloatType> getInterpolatedSource(int t, int level, Interpolation interpolation) {
                ExtendedRandomAccessibleInterval<FloatType, RandomAccessibleInterval< FloatType >>
                        eView = Views.extendZero(getSource( t, level ));
                RealRandomAccessible< FloatType > realRandomAccessible = Views.interpolate( eView, interpolators.get(Interpolation.NEARESTNEIGHBOR) );
                return realRandomAccessible;
            }

            @Override
            public void getSourceTransform(int t, int level, AffineTransform3D affineTransform3D) {
                affineTransform3D.set(affineTransform3D);
                // Complicated! That's the real issue!
                //affineTransform3D.identity();
                // viewer transform
                // Center on the display center of the viewer ...
                // Center on the display center of the viewer ...

                //at3D.scale(1/pixel_size_mm);

                //affineTransform3D.scale(mp.sizePixX, mp.sizePixY, mp.sizePixZ);

                //affineTransform3D.translate(-mp.sX / 2.0, -mp.sY / 2.0, getSlicingAxisPosition());


                //affineTransform3D.translate(-mp.sX/2, -mp.sY/2, -getSlicingAxisPosition());

                //affineTransform3D.scale(1./0.01);

                // Getting an image independent of the view scaling unit (not sure)

                //if (samplingZInPhysicalUnit==0) {
                //    nPz = 1;
                //} else {
                //    nPz = 1+(long)(zSize / (samplingZInPhysicalUnit/2.0)); // TODO : check div by 2
                //}
            }

            @Override
            public FloatType getType() {
                return new FloatType();
            }

            @Override
            public String getName() {
                return "alpha-slice";
            }

            @Override
            public VoxelDimensions getVoxelDimensions() {
                return voxD;
            }

            @Override
            public int getNumMipmapLevels() {
                return 0;
            }
        };
    }


}
