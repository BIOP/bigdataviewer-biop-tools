package ch.epfl.biop.bdv.process;

import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class ResampledSource < T extends NumericType<T> & NativeType<T>> implements Source<T> {
    Source<T> origin;

    Source<?> srcResamplingModel;

    protected final DefaultInterpolators< T > interpolators = new DefaultInterpolators<>();


    public ResampledSource(Source<T> source, Source<T> srcResamplingModel) {
        this.origin=source;
        this.srcResamplingModel=srcResamplingModel;
        System.out.println("resample type = "+source.getType().getClass());

    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t)&&srcResamplingModel.isPresent(t);
    }

    @Override
    public RandomAccessibleInterval<T> getSource(int t, int level) {
        // Get current big dataviewer transformation : source transform and viewer transform
        AffineTransform3D at = new AffineTransform3D(); // Empty Transform
        srcResamplingModel.getSourceTransform(t,0,at);
        //at = at.inverse();

        // Get real random accessible from the source
        final RealRandomAccessible<T> ipimg = origin.getInterpolatedSource(t, 0, Interpolation.NEARESTNEIGHBOR);

        // Gets randomAccessible view ...
        RandomAccessible<T> ra = RealViews.affine(ipimg, at); // Gets the view

        // TODO check is -1 is necessary

        long sx = srcResamplingModel.getSource(t,0).dimension(0)-1;

        long sy = srcResamplingModel.getSource(t,0).dimension(1)-1;

        long sz = srcResamplingModel.getSource(t,0).dimension(2)-1;

        // ... interval
        RandomAccessibleInterval<T> view =
                Views.interval(ra, new long[]{0, 0, 0}, new long[]{sx, sy, sz}); //Sets the interval

        return view;
    }

    @Override
    public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {
        final T zero = getType();
        zero.setZero();
        ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval< T >>
                eView = Views.extendZero(getSource( 0, level ));
        RealRandomAccessible< T > realRandomAccessible = Views.interpolate( eView, interpolators.get(method) );
        return realRandomAccessible;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        srcResamplingModel.getSourceTransform(t,0,transform);

    }

    @Override
    public T getType() {
        return origin.getType();
    }

    @Override
    public String getName() {
        return origin.getName()+"_ResampledLike_"+srcResamplingModel.getName();
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return srcResamplingModel.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        return 1;
    }
}
