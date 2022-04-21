package bdv.util.source.pyramid;

import bdv.AbstractSpimSource;
import bdv.util.DefaultInterpolators;
import bdv.util.ResampledSource;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvSource;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

import java.util.HashMap;
import java.util.Map;

public class PyramidizeXY2Source< T extends NumericType<T> & NativeType<T>> implements Source<T> {

    protected final DefaultInterpolators< T > interpolators = new DefaultInterpolators<>();

    final Source<T> origin;
    final int nResolutionLevels;
    final String name;

    Map<Integer, Map<Integer, RandomAccessibleInterval<T>>> cachedSources = new HashMap<>();

    public PyramidizeXY2Source(Source<T> origin, String name, int nResolutionLevels) { // TODO add cache
        this.origin = origin;
        this.nResolutionLevels = nResolutionLevels;
        this.name = name;
    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t);
    }

    @Override
    public synchronized RandomAccessibleInterval<T> getSource(int t, int level) { // We don't want to enter this multiple times
        if (level==0) {
            return origin.getSource(t,level);
        } else {
            if (!cachedSources.containsKey(t)) {
                cachedSources.put(t, new HashMap<>());
            }
            if (!cachedSources.get(t).containsKey(level)) {
                RandomAccessibleInterval<T> downscaled = downscale2(getSource(t,level-1));
                cachedSources.get(t).put(level, downscaled);
            }
            return cachedSources.get(t).get(level);
        }
    }

    private RandomAccessibleInterval<T> downscale2(RandomAccessibleInterval<T> source) {
        FinalInterval i0 = new FinalInterval(new long[]{0,0,0}, new long[]{source.max(0)-1, source.max(1)-1, source.max(2)});
        FinalInterval ix1 = new FinalInterval(new long[]{1,0,0}, new long[]{source.max(0), source.max(1), source.max(2)});
        FinalInterval iy1 = new FinalInterval(new long[]{0,1,0}, new long[]{source.max(0), source.max(1), source.max(2)});
        FinalInterval ix1y1 = new FinalInterval(new long[]{1,1,0}, new long[]{source.max(0), source.max(1), source.max(2)});
        RandomAccessibleInterval<T> x0 = Views.interval(source, i0);
        RandomAccessibleInterval<T> xp1 = Views.interval(source, ix1);
        RandomAccessibleInterval<T> yp1 = Views.interval(source, iy1);
        RandomAccessibleInterval<T> xp1yp1 = Views.interval(source, ix1y1);
    }

    @Override
    public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {
        final T zero = getType();
        zero.setZero();
        ExtendedRandomAccessibleInterval<T, RandomAccessibleInterval< T >>
                eView = Views.extendZero(getSource( t, level ));
        RealRandomAccessible< T > realRandomAccessible = Views.interpolate( eView, interpolators.get(method) );
        return realRandomAccessible;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        origin.getSourceTransform(t,0,transform);
        if (level!=0) {
            double offsX = transform.get(0, 3);
            double offsY = transform.get(1, 3);
            double offsZ = transform.get(2, 3);
            transform.translate(-offsX, -offsY, -offsZ);
            transform.scale(Math.pow(scaleX, level), Math.pow(scaleY, level), Math.pow(scaleZ, level));
            transform.translate(offsX, offsY, offsZ);
        }
    }

    @Override
    public T getType() {
        return origin.getType();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return origin.getVoxelDimensions();
    }

    @Override
    public int getNumMipmapLevels() {
        return nResolutionLevels;
    }
}
