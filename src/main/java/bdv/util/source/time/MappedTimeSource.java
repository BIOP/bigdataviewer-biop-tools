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
package bdv.util.source.time;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.scijava.service.RenamableSource;
import sc.fiji.bdvpg.scijava.service.tree.inspect.ISourceInspector;
import sc.fiji.bdvpg.service.ISourceService;
import sc.fiji.bdvpg.source.SourceHelper;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static sc.fiji.bdvpg.scijava.service.tree.inspect.SourceInspector.appendInspectorResult;

/**
 * A source which applies an arbitrary transform on time
 * @param <T> the pixel type
 */
public class MappedTimeSource<T> implements Source<T>, ISourceInspector {

    final Source<T> origin;
    Function<Integer, Integer> mappedTime; // mappedTime.apply(t)
    final String name;

    public MappedTimeSource(Source<T> origin, String name, Function<Integer, Integer> mappedTime) {
        this.origin = origin;
        this.mappedTime = mappedTime;
        this.name = name;
    }

    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(mappedTime.apply(t));
    }

    @Override
    public RandomAccessibleInterval<T> getSource(int t, int level) {
        return origin.getSource(mappedTime.apply(t), level);
    }

    @Override
    public RealRandomAccessible<T> getInterpolatedSource(final int t, final int level, final Interpolation method) {
        return origin.getInterpolatedSource(mappedTime.apply(t), level, method);
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        origin.getSourceTransform(mappedTime.apply(t), level, transform);
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
        return origin.getNumMipmapLevels();
    }

    @Override
    public Set<SourceAndConverter<?>> inspect(DefaultMutableTreeNode parent, SourceAndConverter<?> source,
                                              ISourceService source_service,
                                              boolean registerIntermediateSources) {

        DefaultMutableTreeNode originalSource = new DefaultMutableTreeNode("Origin Source"); // This has to be the first one to correctly get the root
        parent.add(originalSource);

        DefaultMutableTreeNode nameNode = new DefaultMutableTreeNode("Name: " + this.name);
        parent.add(nameNode);

        DefaultMutableTreeNode levelRangeNode = new DefaultMutableTreeNode(
                "Mapped-Time Source: [" + mappedTime + "]");
        parent.add(levelRangeNode);

        HashSet<SourceAndConverter<?>> subSources = new HashSet<>();

        if (!source_service.getSourcesFromSpimSource(getOriginSource()).isEmpty()) {
            source_service.getSourcesFromSpimSource(getOriginSource()).forEach((src) -> {
                DefaultMutableTreeNode wrappedSourceNode =
                        new DefaultMutableTreeNode(new RenamableSource(src));
                originalSource.add(wrappedSourceNode);
                subSources.addAll(appendInspectorResult(wrappedSourceNode, src,
                        source_service, registerIntermediateSources));
            });
        } else {
            SourceAndConverter<?> src = SourceHelper.createSourceAndConverter(origin);
            if (registerIntermediateSources) {
                source_service.register(src);
            }
            DefaultMutableTreeNode wrappedSourceNode = new DefaultMutableTreeNode(
                    new RenamableSource(src));
            originalSource.add(wrappedSourceNode);
            subSources.addAll(appendInspectorResult(wrappedSourceNode, src,
                    source_service, registerIntermediateSources));
        }

        return subSources;
    }

    private Source<?> getOriginSource() {
        return origin;
    }

    public static <T> UnaryOperator<T> withName(UnaryOperator<T> op, String name) {
        return new UnaryOperator<T>() {
            @Override
            public T apply(T t) { return op.apply(t); }

            @Override
            public String toString() { return name; }
        };
    }
}
