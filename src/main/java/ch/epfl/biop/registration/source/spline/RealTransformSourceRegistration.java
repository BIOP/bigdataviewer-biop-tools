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
package ch.epfl.biop.registration.source.spline;

import bdv.util.BoundedRealTransform;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.java.utilities.roi.types.RealPointList;
import ch.epfl.biop.registration.source.SourceRegistration;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.RealTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.source.transform.SourceRealTransformer;
import sc.fiji.persist.ScijavaGsonHelper;

import java.util.ArrayList;

abstract public class RealTransformSourceRegistration extends SourceRegistration {

    final static Logger logger = LoggerFactory.getLogger(RealTransformSourceRegistration.class);

    protected RealTransform rt;

    @Override
    public SourceAndConverter[] getTransformedImageMovingToFixed(SourceAndConverter[] img) {
        SourceAndConverter[] out = new SourceAndConverter[img.length];
        SourceRealTransformer srt = new SourceRealTransformer(rt);
        for (int idx = 0;idx<img.length;idx++) {
            out[idx] = srt.apply(img[idx]);
        }
        return out;
    }

    @Override
    public RealPointList getTransformedPtsFixedToMoving(RealPointList pts) {
        RealTransform innerRT = rt; // To unbox bounded realtransform

        // Unbox bounded transform
        if (rt instanceof BoundedRealTransform) {
            innerRT = ((BoundedRealTransform)rt).getTransform().copy();
        }

        ArrayList<RealPoint> cvtList = new ArrayList<>();
        for (RealPoint p : pts.ptList) {
            RealPoint pt3d = new RealPoint(3);
            pt3d.setPosition(new double[]{p.getDoublePosition(0), p.getDoublePosition(1),0});
            innerRT.apply(pt3d, pt3d);
            RealPoint cpt = new RealPoint(pt3d.getDoublePosition(0), pt3d.getDoublePosition(1));
            cvtList.add(cpt);
        }

        return new RealPointList(cvtList);
    }

    public RealTransform getRealTransform() {
        return rt;
    }

    public void setRealTransform(RealTransform transform) {
        this.rt = transform.copy();
    }

    @Override
    final public String getTransform() {
        //logger.debug("Serializing transform of class "+rt.getClass().getSimpleName());
        String transform = ScijavaGsonHelper.getGson(context).toJson(rt, RealTransform.class);
        //logger.debug("Serialization result = "+transform);
        return transform;
    }

    @Override
    final public void setTransform(String serialized_transform) {
        setRealTransform(ScijavaGsonHelper.getGson(context).fromJson(serialized_transform, RealTransform.class));
        isDone = true;
    }

    public RealTransform getTransformAsRealTransform() {
        return rt.copy();
    }

}
