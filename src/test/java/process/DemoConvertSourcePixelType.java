package process;

import bdv.cache.SharedQueue;
import bdv.util.source.process.VoxelProcessedSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceVoxelProcessor;
import loci.common.DebugTools;
import net.imagej.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;

import java.util.concurrent.ConcurrentHashMap;

public class DemoConvertSourcePixelType {

    static public void main(String... args) {
        final ImageJ ij = new ImageJ();

        DebugTools.enableLogging("INFO");
        ij.ui().showUI();
    }

    public static SourceAndConverter<UnsignedShortType> cvt(final SourceAndConverter<UnsignedByteType> source, SharedQueue queue) {

        VoxelProcessedSource.Processor<UnsignedByteType, UnsignedShortType> toUnsignedShort =
                new VoxelProcessedSource.Processor<UnsignedByteType, UnsignedShortType>() {

                    ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, RandomAccessibleInterval<UnsignedShortType>>> cachedRAIs
                            = new ConcurrentHashMap<>();

                    RandomAccessibleInterval<UnsignedShortType> buildSource(RandomAccessibleInterval<UnsignedByteType> rai, int t, int level) {
                        // Make edge display on demand
                        final int[] cellDimensions = new int[]{ 512, 512, 1 };

                        CellGrid grid = new CellGrid(rai.dimensionsAsLongArray(), cellDimensions);
                        UnsignedShortType type = new UnsignedShortType();
                        Cache<Long, Cell<?>> cache = (new GlobalLoaderCache(new Object(), t, level))
                                .withLoader(LoadedCellCacheLoader.get(grid, cell -> {

                                    // Cursor on the source image
                                    final Cursor<UnsignedByteType> inNS = Views.flatIterable( Views.interval( rai, cell ) ).cursor();

                                    // Cursor on output image
                                    final Cursor<UnsignedShortType> out = Views.flatIterable( cell ).cursor();

                                    // Loops through voxels
                                    while ( out.hasNext() ) {
                                        UnsignedByteType v = inNS.next();

                                        out.next().set( (short)Byte.toUnsignedInt(v.getByte()));//(byte) (v.getRealFloat()*10) );
                                    }
                                }, type, AccessFlags.setOf(AccessFlags.VOLATILE)));
                        CachedCellImg img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(PrimitiveType.BYTE, AccessFlags.setOf(AccessFlags.VOLATILE)));

                        return img;
                    }

                    @Override
                    public synchronized RandomAccessibleInterval<UnsignedShortType> process(RandomAccessibleInterval<UnsignedByteType> rai, int t, int level) {
                        if (!cachedRAIs.containsKey(t)) {
                            cachedRAIs.put(t, new ConcurrentHashMap<>());
                        }
                        if (!cachedRAIs.get(t).containsKey(level)) {
                            cachedRAIs.get(t).put(level, buildSource(rai, t, level));
                        }
                        return cachedRAIs.get(t).get(level);
                    }
                };

        return new SourceVoxelProcessor<>(source.getSpimSource().getName()+"_16bits", source, toUnsignedShort, new UnsignedShortType(), queue).get();
    }
}
