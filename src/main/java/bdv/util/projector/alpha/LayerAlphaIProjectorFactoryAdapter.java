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
import org.scijava.plugin.Plugin;
import sc.fiji.persist.IClassRuntimeAdapter;

/**
 * For serialization of {@link LayerAlphaProjectorFactory} objects
 * Runtime adapter for {@link AccumulateProjectorFactory}
 *
 * Used for the serialization of {@link sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions}
 *
 */

@Plugin(type = IClassRuntimeAdapter.class)
public class LayerAlphaIProjectorFactoryAdapter implements IClassRuntimeAdapter<AccumulateProjectorFactory, LayerAlphaIProjectorFactory> {
    @Override
    public Class<? extends AccumulateProjectorFactory> getBaseClass() {
        return AccumulateProjectorFactory.class;
    }

    @Override
    public Class<? extends LayerAlphaIProjectorFactory> getRunTimeClass() {
        return LayerAlphaIProjectorFactory.class;
    }

    public boolean useCustomAdapter() {
        return false;
    }
}
