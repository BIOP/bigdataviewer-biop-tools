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
package bdv.util.projector.alpha;

import bdv.viewer.render.AccumulateProjectorFactory;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Projector which can be used by {@link bdv.BigDataViewer} in order to handle sources transparency and alpha blending
 *
 * For this projector to work as expected, each source should be associated to an alpha source also present in the projector
 *
 * Currently, this repository (bigdataviewer-playground-display) provides a mechanism to semi-conveniently use this projector.
 * Briefly, if a BigDataViewer window is created via the use of {@link sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier},
 * listeners are created which:
 * * synchronizes the display of alpha sources each time a new source is displayed in bdv
 * * in fact, this synchronization mechanism CREATES the alpha source when needed
 * * a caching mechanism using weak keys in {@link sc.fiji.bdvpg.service.SourceServices} allows to reuse
 * alpha sources when needed ( in a different window for instance )
 *
 * Alpha sources {@link bdv.util.source.alpha.IAlphaSource} and {@link bdv.util.source.alpha.AlphaSource} are using
 * {@link FloatType} because these are 32 bits, which allows to trick the projector into fitting a float value into
 * the space of an ARGB int value ( see {@link bdv.util.source.alpha.AlphaConverter} and accumulate method in here
 * which uses `Float.intBitsToFloat(access_alpha.get().get());`
 *
 * In terms of performance, this projector goes around 2.6 x slower than the default projector : 30% for the projector
 * overhead + factor 2 because the number of sources are multiplied by 2.
 *
 * @author Nicolas Chiaruttini, EPFL, 2021
 */

public interface ILayerAlphaProjectorFactory extends AccumulateProjectorFactory<ARGBType> {

    /**
     * Changes sources metadata
     * @param sourcesMeta object which links its source to its layer
     */
    void setSourcesMeta(SourcesMetadata sourcesMeta);

    /**
     * Changes layer metadata
     * @param layerMeta object which links each layer to its alpha value
     */
    void setLayerMeta(LayerMetadata layerMeta);

}
