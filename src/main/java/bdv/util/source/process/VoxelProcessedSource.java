/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.util.source.process;

import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.outofbounds.OutOfBoundsConstantValueFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.scijava.service.RenamableSource;
import sc.fiji.bdvpg.scijava.service.tree.inspect.ISourceInspector;
import sc.fiji.bdvpg.service.ISourceService;
import sc.fiji.bdvpg.source.SourceHelper;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.HashSet;
import java.util.Set;

import static sc.fiji.bdvpg.scijava.service.tree.inspect.SourceInspector.appendInspectorResult;

public class VoxelProcessedSource<I,O extends NumericType<O>> implements Source<O>, ISourceInspector {

    protected final DefaultInterpolators< O > interpolators = new DefaultInterpolators<>();

    final Processor<I, O> processor;

    final Source<I> origin;

    final String name;

    final O o;

    public VoxelProcessedSource(String name, Source<I> origin, Processor<I, O> processor, O o) {
        this.name = name;
        this.origin = origin;
        this.processor = processor;
        this.o = o;
    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t);
    }

    @Override
    public RandomAccessibleInterval<O> getSource(int t, int level) {
        return processor.process(origin.getSource(t,level),t,level);
    }

    @Override
    public RealRandomAccessible<O> getInterpolatedSource(int t, int level, Interpolation method) {
        final O zero = getType();
        zero.setZero();

        ExtendedRandomAccessibleInterval<O, RandomAccessibleInterval< O >>
                eView = Views.extend(getSource( t, level ), new OutOfBoundsConstantValueFactory(zero));//Views.extendZero(getSource( t, level ));
        RealRandomAccessible< O > realRandomAccessible = Views.interpolate( eView, interpolators.get(method) );
        return realRandomAccessible;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        origin.getSourceTransform(t,level,transform);
    }

    @Override
    public O getType() {
        return o.createVariable();
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
        return origin.getNumMipmapLevels();
    }

    /**
     * Returns the origin source that is being processed.
     *
     * @return the origin source
     */
    public Source<I> getOriginSource() {
        return origin;
    }

    /**
     * Returns the processor used for voxel processing.
     *
     * @return the processor
     */
    public Processor<I, O> getProcessor() {
        return processor;
    }

    /**
     * Returns the output type instance.
     *
     * @return the output type
     */
    public O getOutputType() {
        return o;
    }

    @Override
    public Set<SourceAndConverter<?>> inspect(DefaultMutableTreeNode parent, SourceAndConverter<?> source, ISourceService SourceService, boolean registerIntermediateSources) {
        DefaultMutableTreeNode originalSource = new DefaultMutableTreeNode("Origin Source");
        parent.add(originalSource);

        DefaultMutableTreeNode nameNode = new DefaultMutableTreeNode(
                "Name: " + this.name);
        parent.add(nameNode);

        HashSet subSources = new HashSet<>();

        if (!SourceService.getSourcesFromSpimSource(getOriginSource()).isEmpty()) {
            // at least a SourceAndConverter already exists for this source
            SourceService.getSourcesFromSpimSource(getOriginSource()).forEach((src) -> {
                DefaultMutableTreeNode wrappedSourceNode =
                        new DefaultMutableTreeNode(new RenamableSource(src));
                originalSource.add(wrappedSourceNode);
                subSources.addAll(appendInspectorResult(wrappedSourceNode, src,
                        SourceService, registerIntermediateSources));
            });
        } else {
            // no source and converter exist for this source : creates it
            SourceAndConverter<?> src = SourceHelper
                    .createSourceAndConverter(origin);
            if (registerIntermediateSources) {
                SourceService.register(src);
            }
            DefaultMutableTreeNode wrappedSourceNode = new DefaultMutableTreeNode(
                    new RenamableSource(src));
            originalSource.add(wrappedSourceNode);
            subSources.addAll(appendInspectorResult(wrappedSourceNode, src,
                    SourceService, registerIntermediateSources));
        }

        if (this.processor instanceof ISourceInspector) {
            ISourceInspector inspector = (ISourceInspector) processor;
            DefaultMutableTreeNode processorNode = new DefaultMutableTreeNode("Processor");
            inspector.inspect(processorNode, null, SourceService, registerIntermediateSources);
            parent.add(processorNode);
        }
        return subSources;
    }

    public interface Processor<I,O> {
            RandomAccessibleInterval<O> process(RandomAccessibleInterval<I> rai, int t, int level);
    }
}
