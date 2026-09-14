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
package ch.epfl.biop.source.processor;

public class SourcesProcessorHelper {

    public static SourcesProcessor Identity() {
        return new SourcesIdentity();
    }

    /**
     * Applies function from right to left; the most right one is the first to be executed:
     * out = f[0](f[1](f[2]( in )))
     * other notation :
     * out = f[0] o f[1] o f[2] (in)
     * @param fs list of functions for the channel processing
     * @return the concatenated functions
     */
    public static SourcesProcessor compose(SourcesProcessor... fs) {
        SourcesProcessor sp = fs[fs.length-1];
        int idx =  fs.length-1;
        while (idx>0) {
            idx--;
            SourcesProcessor f_next = fs[idx];
            SourcesProcessor f_current = sp;
            sp = new SourcesProcessComposer(f_next, f_current);
        }
        return sp;
    }

    /**
     * Gets rid of all channel selectors in the sources processor
     *
     * Used in registration to set the mask
     *
     * Used in editing to override the original channel being selected
     *
     * Limitation : does not work in a general manner...
     *
     * @param processor the source processor to modify
     * @return an identical processor which do not remove any channel
     */
    public static SourcesProcessor removeChannelsSelect(SourcesProcessor processor) {
        if (processor instanceof SourcesChannelsSelect) {
            return new SourcesIdentity();
        } else if (processor instanceof SourcesProcessComposer) {
            SourcesProcessComposer composer_in = (SourcesProcessComposer) processor;
            return new SourcesProcessComposer(
                    removeChannelsSelect(composer_in.f2),
                    removeChannelsSelect(composer_in.f1));
        } else return processor;
    }

}
