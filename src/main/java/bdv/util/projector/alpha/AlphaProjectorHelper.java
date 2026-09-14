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

import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.real.FloatType;
import org.jetbrains.annotations.NotNull;

public class AlphaProjectorHelper {

    public static Layer getDefaultLayer() {
        return new Layer() {

            @Override
            public float getAlpha() {
                return 1;
            }

            @Override
            public int getBlendingMode() {
                return 0;
            }

            @Override
            public boolean skip() {
                return false;
            }

            @Override
            public int compareTo(@NotNull Layer o) {
                if (this.equals(o)) return 0; // Same object ?
                return -1; // No : then below
            }
        };
    }

    /**
     * Most simple interface.
     * Allows for backward compatibility.
     * This default interface makes this projector equivalent to bdv's default projector
     * @return a default SourcesMetadata implementation with no alpha sources
     */
    public static SourcesMetadata getDefaultSourcesMetadata() {
        return new SourcesMetadata() {
            @Override
            public boolean isAlphaSource(SourceAndConverter<?> source) {
                return false;
            }

            @Override
            public boolean hasAlphaSource(SourceAndConverter<?> source) {
                return false;
            }

            @Override
            public SourceAndConverter<FloatType> getAlphaSource(SourceAndConverter<?> source) {
                return null;
            };
        };
    }

}
