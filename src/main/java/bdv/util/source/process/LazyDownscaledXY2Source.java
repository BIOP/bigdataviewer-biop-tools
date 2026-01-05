package bdv.util.source.process;

import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;

import java.util.HashMap;
import java.util.Map;

/**
 * Lazy downscaling of a source.
 * No downscaling in Z
 * XY gets downscaled by a factor 2 until one dimension goes below 64 pixels (purely arbitrary) or we reach
 * the max downscaling which will be a factor 32 :
 * 1          2          4          8        16       32
 * 512x512 to 256x256 to 128x128 to 64x64 to 32x32 to 16x16
 *
 * @param <T> the pixel type of the source, must be a RealType and NativeType
 */
public class LazyDownscaledXY2Source<T extends RealType<T> & NativeType<T>> implements Source<T> {

    protected final DefaultInterpolators< T > interpolators = new DefaultInterpolators<>();

    final Source<T> origin;

    final String name;

    final int nResolutionLevels;

    Map<Integer, Map<Integer, RandomAccessibleInterval<T>>> sources = new HashMap<>();

    public LazyDownscaledXY2Source(String name, Source<T> origin) {
        this.name = name;
        this.origin = origin;
        long maxX = origin.getSource(0,0).max(0);
        long maxY = origin.getSource(0,0).max(1);
        int nResolutionLevels = 1;
        while ((nResolutionLevels<6)&&(maxX>64)&&(maxY>64)) {
            nResolutionLevels++;
            maxX/=2.0;
            maxY/=2.0;
        }
        this.nResolutionLevels = nResolutionLevels;
    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t);
    }

    private static int tileSize(int level) {
        return (int) Math.pow(2,8-level); // 2^9 = 256
    }

    private void buildSources(int t) {
        sources.put(t, new HashMap<>());
        sources.get(t).put(0, origin.getSource(t,0)); // The origin source should be able to be cached
        for (int level = 1; level<nResolutionLevels; level++) {
            int tileSizeLevel = tileSize(level);
            final int[] cellDimensions = new int[]{ tileSizeLevel, tileSizeLevel, 1 };
            final RandomAccessibleInterval<T> raiBelow = sources.get(t).get(level - 1);
            long[] newDimensions = new long[3];
            newDimensions[0] = raiBelow.dimensionsAsLongArray()[0]/2;
            newDimensions[1] = raiBelow.dimensionsAsLongArray()[1]/2;
            newDimensions[2] = raiBelow.dimensionsAsLongArray()[2];
            CellGrid grid = new CellGrid(newDimensions, cellDimensions);

            // Expand image by one pixel to avoid out of bounds exception
            final RandomAccessibleInterval<T> rai =  Views.expandBorder(raiBelow,1,1,0);

            // Creates shifted views by one pixel in x, y, and x+y : quadrant averaging
            RandomAccessibleInterval<T> rai00 = Views.subsample(rai,2,2,1);
            RandomAccessibleInterval<T> rai01 = Views.subsample(Views.offsetInterval(rai, new long[]{1,0,0}, raiBelow.dimensionsAsLongArray()),2,2,1);
            RandomAccessibleInterval<T> rai10 = Views.subsample(Views.offsetInterval(rai, new long[]{0,1,0}, raiBelow.dimensionsAsLongArray()),2,2,1);
            RandomAccessibleInterval<T> rai11 = Views.subsample(Views.offsetInterval(rai, new long[]{1,1,0}, raiBelow.dimensionsAsLongArray()),2,2,1);

            LoadedCellCacheLoader<T, ?> loader = LoadedCellCacheLoader.get(grid, cell -> {
                // Cursor on the source image
                final Cursor<T> c00 = Views.flatIterable(Views.interval(rai00, cell)).cursor();
                final Cursor<T> c01 = Views.flatIterable(Views.interval(rai01, cell)).cursor();
                final Cursor<T> c10 = Views.flatIterable(Views.interval(rai10, cell)).cursor();
                final Cursor<T> c11 = Views.flatIterable(Views.interval(rai11, cell)).cursor();

                // Cursor on output image
                Cursor<T> out = Views.flatIterable(cell).cursor();

                while (out.hasNext()) {
                    float val =
                             c00.next().getRealFloat()
                            +c01.next().getRealFloat()
                            +c10.next().getRealFloat()
                            +c11.next().getRealFloat();
                    out.next().setReal(val/4.0);
                }

            }, getType().createVariable(), AccessFlags.setOf(AccessFlags.VOLATILE));
            Cache<Long, Cell<T>> cache = (new GlobalLoaderCache(this, t, level)).withLoader(loader);
            CachedCellImg img = new CachedCellImg(grid, getType(), cache, ArrayDataAccessFactory.get(getType(), AccessFlags.setOf(AccessFlags.VOLATILE)));
            sources.get(t).put(level, img);
        }
    }

    @Override
    public synchronized RandomAccessibleInterval<T> getSource(int t, int level) {
        if (!sources.containsKey(t)) {
            buildSources(t);
        }
        return sources.get(t).get(level);
    }

    @Override
    public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {
        final T zero = getType();
        zero.setZero();
        return Views.interpolate( Views.extendZero(getSource( t, level )), interpolators.get(method) );
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        if (level==0) {
            origin.getSourceTransform(t,level,transform);
        } else {
            origin.getSourceTransform(t,0,transform);
            double[] tr = transform.getTranslation();
            transform.translate(-tr[0], -tr[1], -tr[2]);
            transform.scale(Math.pow(2, level), Math.pow(2, level), 1);
            transform.translate(tr);
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
