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
package alpha;

import bdv.spimdata.SpimDataMinimal;
import bdv.util.*;
import bdv.util.source.alpha.AlphaConverter;
import bdv.util.source.alpha.AlphaSource;
import bdv.util.source.alpha.AlphaSourceRAI;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.type.numeric.real.FloatType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DemoAlphaSource {
    static BdvHandle bdvh;

    static Map<SourceAndConverter, SourceAndConverter> sourceToAlpha = new ConcurrentHashMap<>();

    public static void main(final String... args) {
        BdvOptions options = BdvOptions.options();
        //options = options.accumulateProjectorFactory(new DefaultProjectorFactory());
        options = options.accumulateProjectorFactory(new AlphaProjectorFactory(new AlphaProjectorFactory.SourcesMetadata() {
            @Override
            public boolean isAlphaSource(SourceAndConverter source) {
                if (sourceToAlpha.containsValue(source)) return true;
                return false;
            }

            @Override
            public boolean hasAlphaSource(SourceAndConverter source) {
                if (sourceToAlpha.containsKey(source)) {
                    return sourceToAlpha.get(source) != null;
                }
                return false;
            }

            @Override
            public SourceAndConverter getAlphaSource(SourceAndConverter source) {
                if (sourceToAlpha.containsKey(source)) {
                    return sourceToAlpha.get(source);
                }
                return null;
            }
        }));

        AlphaConverter mc = new AlphaConverter();

        AbstractSpimData sd = BdvSampleDatasets.getTestSpimData();

        List<BdvStackSource<?>> stackSources = BdvFunctions.show(sd, options);
        stackSources.get(0).setDisplayRange(0,255);

        bdvh = stackSources.get(0).getBdvHandle();

        Source<FloatType> alpha = new AlphaSourceRAI(stackSources.get(0).getSources().get(0).getSpimSource(), 1f);

        SourceAndConverter<FloatType> alpha_source =
                new SourceAndConverter<>(alpha, mc);

        bdvh.getViewerPanel().state().addSource(alpha_source); // No converter setup
        bdvh.getViewerPanel().state().setSourceActive(alpha_source, true);

        sourceToAlpha.put(bdvh.getViewerPanel().state().getSources().get(0), alpha_source);

        sd = BdvSampleDatasets.getTestSpimData();

        BdvSampleDatasets.shiftSpimData((SpimDataMinimal) sd,20,0);

        stackSources = BdvFunctions.show(sd, options.addTo(bdvh));
        stackSources.get(0).setDisplayRange(0,255);

        AlphaSource alpha_anim = new AlphaSourceRAI(stackSources.get(0).getSources().get(0).getSpimSource(), 0.5f);

        alpha_source = new SourceAndConverter<>(alpha_anim, mc);

        bdvh.getViewerPanel().state().addSource(alpha_source); // No converter setup
        bdvh.getViewerPanel().state().setSourceActive(alpha_source, true);

        sourceToAlpha.put(bdvh.getViewerPanel().state().getSources().get(2), alpha_source);

    }
}
