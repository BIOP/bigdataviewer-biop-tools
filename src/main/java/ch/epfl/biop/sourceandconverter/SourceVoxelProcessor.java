package ch.epfl.biop.sourceandconverter;

import bdv.BigDataViewer;
import bdv.cache.SharedQueue;
import bdv.util.RAIHelper;
import bdv.util.VolatileSource;
import bdv.util.source.process.VoxelProcessedSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.img.Img;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class SourceVoxelProcessor<I extends NumericType<I>,O extends NumericType<O>> implements Runnable, Function<SourceAndConverter<I>, SourceAndConverter<O>> {

    final VoxelProcessedSource.Processor processor;
    final SourceAndConverter<I> source_in;
    final String name;
    final O pixel;
    private int nThreads;

    public SourceVoxelProcessor(String name,
                                SourceAndConverter<I> source_in,
                                VoxelProcessedSource.Processor processor,
                                O pixel,
                                int nThreads) {
        this.processor = processor;
        this.source_in = source_in;
        this.name = name;
        this.pixel = pixel;
        this.nThreads = nThreads;
    }

    @Override
    public void run() {

    }

    public SourceAndConverter<O> get() {
        return apply(source_in);
    }

    @Override
    public SourceAndConverter<O> apply(final SourceAndConverter<I> sac) {

        Source<O> srcProcessed = new VoxelProcessedSource<I,O>(name, sac.getSpimSource(), processor, pixel);

        SourceAndConverter<O> sac_out;

        SourceAndConverter<?> vsac;
        Source<?> vsrcRsampled;

        vsrcRsampled = new VolatileSource<>(srcProcessed, new SharedQueue(nThreads));

        vsac = new SourceAndConverter(vsrcRsampled,
                BigDataViewer.createConverterToARGB((NumericType)vsrcRsampled.getType()));
        sac_out = new SourceAndConverter(srcProcessed,
                BigDataViewer.createConverterToARGB(srcProcessed.getType()),vsac);

        return sac_out;
    }

    public static <T extends NumericType<T>> SourceAndConverter<UnsignedByteType> getBorders(final SourceAndConverter<T> source) {

        VoxelProcessedSource.Processor<T, UnsignedByteType> borderMaker =
        new VoxelProcessedSource.Processor<T, UnsignedByteType>() {

            ConcurrentHashMap<Integer, ConcurrentHashMap<Integer,RandomAccessibleInterval<UnsignedByteType>>> cachedRAIs
                    = new ConcurrentHashMap<>();

            RandomAccessibleInterval<UnsignedByteType> buildSource(RandomAccessibleInterval<T> rai, int t, int level) {
                // Make edge display on demand
                final int[] cellDimensions = new int[]{ 32, 32, 32 };

                // Expand label image by one pixel to avoid out of bounds exception
                final RandomAccessibleInterval<T> lblImgWithBorder =  Views.expandBorder(rai,new long[]{1,1,1});

                // Creates shifted views by one pixel in each dimension
                RandomAccessibleInterval<T> lblImgXShift = Views.translate(lblImgWithBorder,new long[]{1,0,0});
                RandomAccessibleInterval<T> lblImgYShift = Views.translate(lblImgWithBorder,new long[]{0,1,0});
                RandomAccessibleInterval<T> lblImgZShift = Views.translate(lblImgWithBorder,new long[]{0,0,1});

                //final int[] cellDimensions = new int[]{ 32, 32, 32 };

                CellGrid grid = new CellGrid(rai.dimensionsAsLongArray(), cellDimensions);
                UnsignedByteType type = new UnsignedByteType();
                Cache<Long, Cell<?>> cache = (new GlobalLoaderCache(new Object(), t, level))
                        .withLoader(LoadedCellCacheLoader.get(grid, cell -> {

                            // Cursor on the source image
                            final Cursor<T> inNS = Views.flatIterable( Views.interval( rai, cell ) ).cursor();

                            // Cursor on shifted source image
                            final Cursor<T> inXS = Views.flatIterable( Views.interval( lblImgXShift, cell ) ).cursor();
                            final Cursor<T> inYS = Views.flatIterable( Views.interval( lblImgYShift, cell ) ).cursor();
                            final Cursor<T> inZS = Views.flatIterable( Views.interval( lblImgZShift, cell ) ).cursor();

                            // Cursor on output image
                            final Cursor<UnsignedByteType> out = Views.flatIterable( cell ).cursor();

                            // Loops through voxels
                            while ( out.hasNext() ) {
                                T v = inNS.next();
                                if (!v.equals(inXS.next())) {
                                    out.next().set( (byte) 126 );
                                    inYS.next();
                                    inZS.next();
                                } else {
                                    if (!v.equals(inYS.next())) {
                                        out.next().set( (byte) 126 );
                                        inZS.next();
                                    } else {
                                        if (!v.equals(inZS.next())) {
                                            out.next().set( (byte) 126 );
                                        } else {
                                            out.next();
                                        }
                                    }
                                }
                            }
                        }, type, AccessFlags.setOf(AccessFlags.VOLATILE)));
                CachedCellImg img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(PrimitiveType.BYTE, AccessFlags.setOf(AccessFlags.VOLATILE)));

                return img;
            }

            @Override
            public synchronized RandomAccessibleInterval<UnsignedByteType> process(RandomAccessibleInterval<T> rai, int t, int level) {
                if (!cachedRAIs.containsKey(t)) {
                    cachedRAIs.put(t, new ConcurrentHashMap<>());
                }
                if (!cachedRAIs.get(t).containsKey(level)) {
                    cachedRAIs.get(t).put(level, buildSource(rai, t, level));
                }
                return cachedRAIs.get(t).get(level);
            }
        };

        return new SourceVoxelProcessor<>("Borders_"+source.getSpimSource().getName(), source, borderMaker, new UnsignedByteType(), Runtime.getRuntime().availableProcessors()-1).get();
    }
}