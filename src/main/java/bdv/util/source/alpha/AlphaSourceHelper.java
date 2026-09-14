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
package bdv.util.source.alpha;

import bdv.img.WarpedSource;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.real.FloatType;
import sc.fiji.bdvpg.service.ISourceService;
import sc.fiji.bdvpg.service.SourceServices;

import java.util.List;
import java.util.Optional;

/**
 * Helper function which creates or retrieves {@link IAlphaSource} linked to potentially
 * any {@link Source}. This helper uses the weak keys cache of
 * bigdataviewer playground's {@link sc.fiji.bdvpg.scijava.service.SourceService}
 * to avoid re-creating multiple alpha sources.
 * <p>
 * {@link WarpedSource} as well as TransformedSource {@link TransformedSource} are supported
 *
 * @author Nicolas Chiaruttini, EPFL, 2021
 */

public class AlphaSourceHelper {

    final public static String ALPHA_SOURCE_KEY = "ALPHA_SOURCE";

    public static synchronized SourceAndConverter<FloatType> getOrBuildAlphaSource(Source<?> source) {

        if (source instanceof IAlphaSource) {
            throw new UnsupportedOperationException("Error : you can't make an alpha source out of an alpha source "+source.getName());
        }
        ISourceService sourceService = SourceServices.getSourceService();

        List<SourceAndConverter<?>> sourceList = sourceService.getSourcesFromSpimSource(source);

        Optional<SourceAndConverter<?>> source_already_associated_with_alpha = sourceList.stream().filter(src -> getExistingAlphaSource(src)!=null).findFirst();

        // Deal done
        if (source_already_associated_with_alpha.isPresent()) {
            //Alpha source already computed, returning it
            return getExistingAlphaSource(source_already_associated_with_alpha.get());
        }

        // Builds new alpha source, one way only for now
        IAlphaSource alpha;
        if (source instanceof WarpedSource) {
            //Warped alpha
            alpha = new AlphaSourceWarped(source, 1f);
        } else if (source instanceof TransformedSource) {
            //System.out.println("Transformed alpha");
            //System.out.println("The transformed source is "+source.getName());
            //System.out.println("The wrapped transformed source is "+((TransformedSource<?>) source).getWrappedSource().getName());

            IAlphaSource iniAlpha = (IAlphaSource) getOrBuildAlphaSource(((TransformedSource<?>) source).getWrappedSource()).getSpimSource();
            alpha = new AlphaSourceTransformed(iniAlpha, (TransformedSource<?>) source);
        } else {
            alpha = new AlphaSourceRAI(source, 1f);
        }
        SourceAndConverter<FloatType> alpha_source = new SourceAndConverter<>(alpha, new AlphaConverter());

        sourceList.forEach(compatibleSac -> SourceServices.getSourceService().setMetadata(compatibleSac, ALPHA_SOURCE_KEY, alpha_source));

        return alpha_source;
    }

    public static synchronized void setAlphaSource(SourceAndConverter<?> source, IAlphaSource alphaSource) {
        SourceServices.getSourceService().setMetadata(source, ALPHA_SOURCE_KEY, new SourceAndConverter<>(alphaSource, new AlphaConverter()));
    }

    public static synchronized void setAlphaSource(SourceAndConverter<?> source, SourceAndConverter<FloatType> alphaSource) {
        SourceServices.getSourceService().setMetadata(source, ALPHA_SOURCE_KEY, alphaSource);
    }

    // synchronized recursive calls are legit in Java
    public static synchronized SourceAndConverter<FloatType> getOrBuildAlphaSource(SourceAndConverter<?> source) {
        return getOrBuildAlphaSource(source.getSpimSource());
    }

    static SourceAndConverter<FloatType> getExistingAlphaSource(SourceAndConverter<?> source) {
        ISourceService sourceService = SourceServices.getSourceService();
        if (sourceService.containsMetadata(source, ALPHA_SOURCE_KEY)) {
            return (SourceAndConverter<FloatType>) sourceService.getMetadata(source, ALPHA_SOURCE_KEY);
        }
        return null;
    }
}
