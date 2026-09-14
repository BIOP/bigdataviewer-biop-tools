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
package ch.epfl.biop.source.transform;

import bdv.img.WarpedSource;
import bdv.util.Elliptical3DTransform;
import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.BoundingBoxEstimation;
import sc.fiji.bdvpg.service.SourceServices;

import java.util.function.Function;

public class Elliptic3DTransformer implements Runnable, Function<SourceAndConverter, SourceAndConverter> {

    SourceAndConverter sourceIn;
    Elliptical3DTransform e3Dt;
    SourceAndConverter sourceOut;

    public Elliptic3DTransformer(SourceAndConverter src, Elliptical3DTransform e3Dt) {
        this.sourceIn = src;
        this.e3Dt = e3Dt;
    }

    @Override
    public void run() {
        sourceOut = apply(sourceIn);
    }

    public SourceAndConverter getSourceOut() {
        return sourceOut;
    }

    public SourceAndConverter apply(SourceAndConverter in) {
        WarpedSource ws = new WarpedSource(in.getSpimSource(), "Transform_"+e3Dt.getName()+"_"+in.getSpimSource().getName());
        ws.setBoundingBoxEstimator(new BoundingBoxEstimation(BoundingBoxEstimation.Method.CORNERS));
        ws.updateTransform(e3Dt);
        ws.setIsTransformed(true);

        if (in.asVolatile()!=null) {
            WarpedSource vws = new WarpedSource(in.asVolatile().getSpimSource(), "Transform_"+e3Dt.getName()+"_"+in.asVolatile().getSpimSource().getName());//f.apply(in.asVolatile().getSpimSource());
            vws.setBoundingBoxEstimator(new BoundingBoxEstimation(BoundingBoxEstimation.Method.CORNERS));
            vws.updateTransform(e3Dt);
            vws.setIsTransformed(true);

            SourceAndConverter vout = new SourceAndConverter<>(vws, in.asVolatile().getConverter());

            SourceAndConverter out = new SourceAndConverter(ws, in.getConverter(), vout);

            e3Dt.updateNotifiers.add(() -> {
                ws.updateTransform(e3Dt);
                vws.updateTransform(e3Dt);
                SourceServices
                        .getBdvDisplayService()
                        .getDisplaysOf(out).forEach(bdvHandle -> bdvHandle.getViewerPanel().requestRepaint());
            }); // TODO avoid memory leak...

            SourceServices.getSourceService().register(out);
            return out;
        } else {

            SourceAndConverter out = new SourceAndConverter(ws, in.getConverter());

            e3Dt.updateNotifiers.add(() -> {
                ws.updateTransform(e3Dt);
                SourceServices
                        .getBdvDisplayService()
                        .getDisplaysOf(out).forEach(bdvHandle -> bdvHandle.getViewerPanel().requestRepaint());
            }); // TODO avoid memory leak...

            SourceServices.getSourceService().register(out);
            return out;
        }

    }

}
