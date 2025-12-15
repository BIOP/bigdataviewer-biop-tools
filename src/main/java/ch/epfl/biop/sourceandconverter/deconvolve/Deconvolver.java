/*-
 * #%L
 * Tiled GPU Deconvolution for BigDataViewer-Playground - BIOP - EPFL
 * %%
 * Copyright (C) 2024 - 2025 EPFL
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package ch.epfl.biop.sourceandconverter.deconvolve;

import bdv.cache.SharedQueue;
import bdv.util.source.process.VoxelProcessedSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.SourceVoxelProcessor;
import net.haesleinhuepf.clijx.imglib2cache.Clij2RichardsonLucyImglib2Cache;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Deconvolver {

    public static <T extends RealType<T>> SourceAndConverter<FloatType> getDeconvolved(final SourceAndConverter<T> source,
                                                                                       String name,
                                                                                       int[] cellDimensions,
                                                                                       Clij2RichardsonLucyImglib2Cache.Builder deconvolveBuilder,
                                                                                       SharedQueue queue) {

        List<Clij2RichardsonLucyImglib2Cache<FloatType, T, T>> ops = new ArrayList<>();

        int numTimepoints = SourceAndConverterHelper.getMaxTimepoint(source)+1;

        // create the version of clij2 RL that works on cells
        for (int t = 0; t<numTimepoints; t++) {
            // One op per timepoint, but because the same builder is reused, the same gpu pool will be shared
            ops.add((Clij2RichardsonLucyImglib2Cache<FloatType, T, T>) deconvolveBuilder.rai(source.getSpimSource().getSource(t,0)).build());
        }

        VoxelProcessedSource.Processor<T, FloatType> deconvolver =
                new VoxelProcessedSource.Processor<T, FloatType>() {

                    ConcurrentHashMap<Integer, ConcurrentHashMap<Integer,RandomAccessibleInterval<FloatType>>> cachedRAIs
                            = new ConcurrentHashMap<>();

                    RandomAccessibleInterval<FloatType> buildSource(RandomAccessibleInterval<T> rai, int t, int level) {

                        CellGrid grid = new CellGrid(rai.dimensionsAsLongArray(), cellDimensions);
                        FloatType type = new FloatType();
                        Cache<Long, Cell<?>> cache = (new GlobalLoaderCache(new Object(), t, level))
                                .withLoader(LoadedCellCacheLoader.get(grid, cell -> {
                                        ops.get(t).accept(cell);
                                }, type, AccessFlags.setOf(AccessFlags.VOLATILE)));
                        CachedCellImg img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(PrimitiveType.BYTE, AccessFlags.setOf(AccessFlags.VOLATILE)));
                        return img;
                    }

                    @Override
                    public synchronized RandomAccessibleInterval<FloatType> process(RandomAccessibleInterval<T> rai, int t, int level) {
                        if (!cachedRAIs.containsKey(t)) {
                            cachedRAIs.put(t, new ConcurrentHashMap<>());
                        }
                        if (!cachedRAIs.get(t).containsKey(level)) {
                            cachedRAIs.get(t).put(level, buildSource(rai, t, level));
                        }
                        return cachedRAIs.get(t).get(level);
                    }
                };

        return new SourceVoxelProcessor<>(name, source, deconvolver, new FloatType(), queue).get();
    }

    public static <T extends RealType<T>&NativeType<T>> SourceAndConverter<T> getDeconvolvedCast(final SourceAndConverter<T> source,
                                                                                                                 String name,
                                                                                                                 int[] cellDimensions,
                                                                                                                 Clij2RichardsonLucyImglib2Cache.Builder deconvolveBuilder,
                                                                                                                 SharedQueue queue) {
        List<Clij2RichardsonLucyImglib2Cache<FloatType, T, T>> ops = new ArrayList<>();

        int numTimepoints = SourceAndConverterHelper.getMaxTimepoint(source)+1;

        // create the version of clij2 RL that works on cells
        for (int t = 0; t<numTimepoints; t++) {
            // One op per timepoint, but because the same builder is reused, the same gpu pool will be shared
            ops.add((Clij2RichardsonLucyImglib2Cache<FloatType, T, T>) deconvolveBuilder.rai(source.getSpimSource().getSource(t,0)).build());
        }

        VoxelProcessedSource.Processor<T, T> deconvolver =
                new VoxelProcessedSource.Processor<T, T>() {

                    ConcurrentHashMap<Integer, ConcurrentHashMap<Integer,RandomAccessibleInterval<T>>> cachedRAIs = new ConcurrentHashMap<>();

                    RandomAccessibleInterval<T> buildSource(RandomAccessibleInterval<T> rai, int t, int level) {

                        CellGrid grid = new CellGrid(rai.dimensionsAsLongArray(), cellDimensions);
                        T type = rai.getType().duplicateTypeOnSameNativeImg();
                        Cache<Long, Cell<T>> cache = (new GlobalLoaderCache(new Object(), t, level))
                                .withLoader(LoadedCellCacheLoader.get(grid, cell -> {
                                    long[] dimensions = Intervals.dimensionsAsLongArray(cell);
                                    ArrayImg<FloatType, FloatArray> tempArray = ArrayImgs.floats(dimensions);

                                    // Translate to match cell's position
                                    long[] offset = new long[cell.numDimensions()];
                                    cell.min(offset);
                                    RandomAccessibleInterval<FloatType> tempCell = Views.translate(tempArray, offset);

                                    // Run deconvolution on temp cell
                                    ops.get(t).accept(tempCell);

                                    // Convert FloatType back to original type T
                                    Cursor<FloatType> source = tempCell.cursor();
                                    Cursor<T> target = cell.cursor();

                                    while (source.hasNext()) {
                                        target.next().setReal(source.next().getRealFloat());
                                    }

                                }, type, AccessFlags.setOf(AccessFlags.VOLATILE)));
                        CachedCellImg img = new CachedCellImg(grid, type , cache,
                                ArrayDataAccessFactory.get(getPrimitiveType(type),
                                AccessFlags.setOf(AccessFlags.VOLATILE)));
                        return img;
                    }

                    @Override
                    public synchronized RandomAccessibleInterval<T> process(RandomAccessibleInterval<T> rai, int t, int level) {
                        if (!cachedRAIs.containsKey(t)) {
                            cachedRAIs.put(t, new ConcurrentHashMap<>());
                        }
                        if (!cachedRAIs.get(t).containsKey(level)) {
                            cachedRAIs.get(t).put(level, buildSource(rai, t, level));
                        }
                        return cachedRAIs.get(t).get(level);
                    }
                };

        return new SourceVoxelProcessor<>(name, source, deconvolver, source.getSpimSource().getType(), queue).get();
    }

    private static <T extends RealType<T>> PrimitiveType getPrimitiveType(T type) {
        if (type instanceof ByteType || type instanceof UnsignedByteType) {
            return PrimitiveType.BYTE;
        } else if (type instanceof ShortType || type instanceof UnsignedShortType) {
            return PrimitiveType.SHORT;
        } else if (type instanceof IntType || type instanceof UnsignedIntType) {
            return PrimitiveType.INT;
        } else if (type instanceof LongType || type instanceof UnsignedLongType) {
            return PrimitiveType.LONG;
        } else if (type instanceof FloatType) {
            return PrimitiveType.FLOAT;
        } else if (type instanceof DoubleType) {
            return PrimitiveType.DOUBLE;
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type.getClass());
        }
    }
}
