package ch.epfl.biop.bdv.process;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;

public class BDVSourceAffineTransformed<T> implements Source<T> {

    public Source<T> origin;
    public AffineTransform3D transform;

    public BDVSourceAffineTransformed(Source<T> org, AffineTransform3D at) {
        origin = org;
        transform = at;
    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t);
    }

    @Override
    public RandomAccessibleInterval<T> getSource(int t, int level) {
        return origin.getSource(t,level);
    }

    @Override
    public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {
        return origin.getInterpolatedSource(t,level,method);
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        AffineTransform3D atOrigin = new AffineTransform3D();
        origin.getSourceTransform(t,level,atOrigin);
        transform.identity();
        transform.concatenate(this.transform);
        transform.concatenate(atOrigin);
    }

    @Override
    public T getType() {
        return origin.getType();
    }

    @Override
    public String getName() {
        return origin.getName();
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return origin.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        return origin.getNumMipmapLevels();
    }
}
