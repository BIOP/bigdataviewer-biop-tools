/*-
 * #%L
 * BigDataViewer-Playground
 * %%
 * Copyright (C) 2019 - 2026 Nicolas Chiaruttini, EPFL - Robert Haase, MPI CBG - Christian Tischer, EMBL
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

package sc.fiji.bdvpg.sourceandconverter.transform;

import bdv.cache.SharedQueue;
import bdv.util.source.labkit.LabkitSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.converter.Converter;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import org.scijava.Context;
import bdv.util.VolatileSource;
import sc.fiji.labkit.ui.segmentation.Segmenter;

import java.util.List;
import java.util.function.Function;

/**
 * Action class that applies a Labkit classifier to input sources and produces
 * a lazy segmentation SourceAndConverter.
 * <p>
 * The classifier is applied on-demand as tiles are requested, making it
 * efficient for large datasets.
 * </p>
 */
public class SourceLabkitClassifier implements Runnable, Function<SourceAndConverter<?>[], SourceAndConverter<UnsignedByteType>> {

    private final SourceAndConverter<?>[] sources;
    private final String classifierPath;
    private final Context context;
    private final int resolutionLevel;
    private final String name;
    private final Segmenter segmenter;

    private SourceAndConverter<UnsignedByteType> result;

    /**
     * Creates a SourceLabkitClassifier action.
     *
     * @param sources the input sources (each source represents a channel)
     * @param classifierPath path to the Labkit .classifier file
     * @param context the SciJava context
     * @param name the name for the output source
     * @param resolutionLevel the resolution level to use from the input sources
     */
    public SourceLabkitClassifier(SourceAndConverter<?>[] sources, String classifierPath, Context context, String name, int resolutionLevel) {
        this.sources = sources;
        this.classifierPath = classifierPath;
        this.context = context;
        this.name = name;
        this.resolutionLevel = resolutionLevel;
        this.segmenter = null;
    }

    /**
     * Creates a SourceLabkitClassifier action with a pre-loaded segmenter.
     *
     * @param sources the input sources (each source represents a channel)
     * @param segmenter the pre-loaded Labkit segmenter
     * @param name the name for the output source
     * @param resolutionLevel the resolution level to use from the input sources
     */
    public SourceLabkitClassifier(SourceAndConverter<?>[] sources, Segmenter segmenter, String name, int resolutionLevel) {
        this.sources = sources;
        this.classifierPath = null;
        this.context = null;
        this.name = name;
        this.resolutionLevel = resolutionLevel;
        this.segmenter = segmenter;
    }

    @Override
    public void run() {
        result = apply(sources);
    }

    /**
     * Returns the result of the classification.
     * Must be called after {@link #run()}.
     *
     * @return the SourceAndConverter containing the lazy segmentation
     */
    public SourceAndConverter<UnsignedByteType> get() {
        if (result == null) {
            run();
        }
        return result;
    }

    /**
     * Returns the class names from the classifier.
     *
     * @return list of class names
     */
    public List<String> getClassNames() {
        if (result != null) {
            return ((LabkitSource) result.getSpimSource()).getClassNames();
        }
        return null;
    }

    @Override
    public SourceAndConverter<UnsignedByteType> apply(SourceAndConverter<?>[] srcs) {
        // Create the LabkitSource
        Source<UnsignedByteType> labkitSource;
        if (segmenter != null) {
            labkitSource = new LabkitSource(name, srcs, segmenter, resolutionLevel);
        } else {
            labkitSource = new LabkitSource(name, srcs, classifierPath, context, resolutionLevel);
        }

        // Create a simple grayscale converter for the segmentation result
        Converter<UnsignedByteType, ARGBType> converter
                //createConverter();
                //SourceAndConverterHelper.createConverter(labkitSource);
                = RealARGBColorConverter.create(new UnsignedByteType(), 0, 255);
                //converter.setColor(new ARGBType(0xffffffff));


        // Check if any input source has a volatile version
        boolean hasVolatile = false;
        for (SourceAndConverter<?> src : srcs) {
            if (src.asVolatile() != null) {
                hasVolatile = true;
                break;
            }
        }

        SourceAndConverter<UnsignedByteType> sac;
        if (hasVolatile) {
            // Create volatile version
            Source<VolatileUnsignedByteType> volatileSource = new VolatileSource<>(
                    labkitSource,
                    new VolatileUnsignedByteType(),
                    new SharedQueue(Math.max(Runtime.getRuntime().availableProcessors() - 1, 1))
            );

            Converter<VolatileUnsignedByteType, ARGBType> volatileConverter =
                    //createVolatileConverter();
            //Converter<UnsignedByteType, ARGBType> converter
                    //createConverter();
                    //SourceAndConverterHelper.createConverter(labkitSource);
                    RealARGBColorConverter.create(new VolatileUnsignedByteType(), 0, 255);

            SourceAndConverter<VolatileUnsignedByteType> vsac = new SourceAndConverter<>(volatileSource, volatileConverter);
            sac = new SourceAndConverter<>(labkitSource, converter, vsac);
        } else {
            sac = new SourceAndConverter<>(labkitSource, converter);
        }

        return sac;
    }

    /**
     * Creates a converter for displaying segmentation results.
     * Maps class indices to grayscale values.
     */
    /*private Converter<UnsignedByteType, ARGBType> createConverter() {
        return (input, output) -> {
            int value = input.get();
            // Scale to visible grayscale (0 = black, higher classes = brighter)
            int gray = Math.min(255, value * 85); // 0, 85, 170, 255 for classes 0-3
            output.set(ARGBType.rgba(gray, gray, gray, 255));
        };
    }*/

    /**
     * Creates a volatile converter for displaying segmentation results.
     */
    /*private Converter<VolatileUnsignedByteType, ARGBType> createVolatileConverter() {
        return (input, output) -> {
            if (input.isValid()) {
                int value = input.get().get();
                int gray = Math.min(255, value * 85);
                output.set(ARGBType.rgba(gray, gray, gray, 255));
            } else {
                output.set(0);
            }
        };
    }*/
}
