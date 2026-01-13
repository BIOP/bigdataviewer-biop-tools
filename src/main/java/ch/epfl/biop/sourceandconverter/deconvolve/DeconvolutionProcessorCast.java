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

import bdv.util.source.process.VoxelProcessedSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
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
import net.imglib2.type.numeric.integer.*;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;
import sc.fiji.bdvpg.scijava.services.ui.RenamableSourceAndConverter;
import sc.fiji.bdvpg.scijava.services.ui.inspect.ISourceInspector;
import sc.fiji.bdvpg.services.ISourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterInspector.appendInspectorResult;

/**
 * A named processor class for Richardson-Lucy deconvolution that casts the output
 * back to the original pixel type.
 * <p>
 * This class encapsulates all deconvolution parameters needed to reconstruct
 * the processor from serialized data.
 * </p>
 *
 * @param <T> the input and output pixel type
 */
public class DeconvolutionProcessorCast<T extends RealType<T> & NativeType<T>> implements VoxelProcessedSource.Processor<T, T>, ISourceInspector {

    private final int[] cellDimensions;
    private final int[] overlap;
    private final int numIterations;
    private final boolean nonCirculant;
    private final float regularizationFactor;
    private final SourceAndConverter<? extends RealType<?>> psfSource;

    // Cached operations per timepoint (lazily initialized)
    private transient List<Clij2RichardsonLucyImglib2Cache<FloatType, T, T>> ops;
    private transient SourceAndConverter<T> source;
    private transient ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, RandomAccessibleInterval<T>>> cachedRAIs;

    /**
     * Creates a new DeconvolutionProcessorCast.
     *
     * @param cellDimensions the cell dimensions for tiled processing
     * @param overlap the overlap between tiles
     * @param numIterations number of Richardson-Lucy iterations
     * @param nonCirculant whether to use non-circulant boundary conditions
     * @param regularizationFactor regularization factor to prevent noise amplification
     * @param psfSource the point spread function as a SourceAndConverter
     */
    public DeconvolutionProcessorCast(int[] cellDimensions, int[] overlap, int numIterations,
                                       boolean nonCirculant, float regularizationFactor,
                                       SourceAndConverter<? extends RealType<?>> psfSource) {
        this.cellDimensions = cellDimensions;
        this.overlap = overlap;
        this.numIterations = numIterations;
        this.nonCirculant = nonCirculant;
        this.regularizationFactor = regularizationFactor;
        this.psfSource = psfSource;
        this.cachedRAIs = new ConcurrentHashMap<>();
    }

    /**
     * Initializes the processor for a specific source.
     * Must be called before processing.
     *
     * @param source the source to process
     */
    public void initialize(SourceAndConverter<T> source) {
        this.source = source;
        this.ops = new ArrayList<>();
        this.cachedRAIs = new ConcurrentHashMap<>();

        int numTimepoints = SourceAndConverterHelper.getMaxTimepoint(source) + 1;

        // Extract PSF RAI from the SourceAndConverter (timepoint 0, resolution level 0)
        RandomAccessibleInterval<? extends RealType<?>> psfRAI =
                (RandomAccessibleInterval<? extends RealType<?>>) psfSource.getSpimSource().getSource(0, 0);

        Clij2RichardsonLucyImglib2Cache.Builder builder = Clij2RichardsonLucyImglib2Cache.builder()
                .nonCirculant(nonCirculant)
                .numberOfIterations(numIterations)
                .psf(psfRAI)
                .overlap(overlap[0], overlap[1], overlap[2])
                .regularizationFactor(regularizationFactor);

        for (int t = 0; t < numTimepoints; t++) {
            ops.add((Clij2RichardsonLucyImglib2Cache<FloatType, T, T>) builder.rai(source.getSpimSource().getSource(t, 0)).build());
        }
    }

    /**
     * Checks if the processor has been initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return ops != null && source != null;
    }

    private RandomAccessibleInterval<T> buildSource(RandomAccessibleInterval<T> rai, int t, int level) {
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
                    Cursor<FloatType> srcCursor = tempCell.cursor();
                    Cursor<T> tgtCursor = cell.cursor();

                    while (srcCursor.hasNext()) {
                        tgtCursor.next().setReal(srcCursor.next().getRealFloat());
                    }

                }, type, AccessFlags.setOf(AccessFlags.VOLATILE)));
        CachedCellImg img = new CachedCellImg(grid, type, cache,
                ArrayDataAccessFactory.get(getPrimitiveType(type), AccessFlags.setOf(AccessFlags.VOLATILE)));
        return img;
    }

    @Override
    public synchronized RandomAccessibleInterval<T> process(RandomAccessibleInterval<T> rai, int t, int level) {
        if (!isInitialized()) {
            throw new IllegalStateException("DeconvolutionProcessorCast must be initialized before processing");
        }
        if (!cachedRAIs.containsKey(t)) {
            cachedRAIs.put(t, new ConcurrentHashMap<>());
        }
        if (!cachedRAIs.get(t).containsKey(level)) {
            cachedRAIs.get(t).put(level, buildSource(rai, t, level));
        }
        return cachedRAIs.get(t).get(level);
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

    // Getters for serialization

    public int[] getCellDimensions() {
        return cellDimensions;
    }

    public int[] getOverlap() {
        return overlap;
    }

    public int getNumIterations() {
        return numIterations;
    }

    public boolean isNonCirculant() {
        return nonCirculant;
    }

    public float getRegularizationFactor() {
        return regularizationFactor;
    }

    public SourceAndConverter<? extends RealType<?>> getPsfSource() {
        return psfSource;
    }

    public SourceAndConverter<? extends RealType<?>> getRawSource() {
        return source;
    }

    @Override
    public Set<SourceAndConverter<?>> inspect(DefaultMutableTreeNode parent, SourceAndConverter<?> sac,
                                               ISourceAndConverterService sourceAndConverterService,
                                               boolean registerIntermediateSources) {
        parent.add(new DefaultMutableTreeNode("Cell Dimensions: " + Arrays.toString(cellDimensions)));
        parent.add(new DefaultMutableTreeNode("Overlap: " + Arrays.toString(overlap)));
        parent.add(new DefaultMutableTreeNode("Iterations: " + numIterations));
        parent.add(new DefaultMutableTreeNode("Non-Circulant: " + nonCirculant));
        parent.add(new DefaultMutableTreeNode("Regularization Factor: " + regularizationFactor));
        parent.add(new DefaultMutableTreeNode("PSF Source: " + psfSource.getSpimSource().getName()));
        parent.add(new DefaultMutableTreeNode("Output Type: Original (cast)"));

        DefaultMutableTreeNode sourceNode = new DefaultMutableTreeNode(
                new RenamableSourceAndConverter(psfSource));
        appendInspectorResult(sourceNode, psfSource,
                sourceAndConverterService, registerIntermediateSources);
        parent.add(sourceNode);

        Set<SourceAndConverter<?>> subSources = new HashSet<>();
        subSources.add((SourceAndConverter<?>) psfSource);
        return subSources;
    }
}
