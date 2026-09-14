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
package ch.epfl.biop.registration.source.affine;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.java.utilities.roi.types.RealPointList;
import ch.epfl.biop.registration.source.SourceRegistration;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import sc.fiji.bdvpg.source.SourceAndTimeRange;
import sc.fiji.bdvpg.source.transform.SourceTransformHelper;
import sc.fiji.persist.ScijavaGsonHelper;

import java.util.ArrayList;

/**
 * Abstract base class for registrations that use affine transformations.
 * Provides common functionality for affine-based source and converter registrations.
 */
abstract public class AffineTransformSourceRegistration extends SourceRegistration {

    /** The affine transformation applied by this registration. */
    protected AffineTransform3D at3d = new AffineTransform3D();

    /** The time point at which the registration is applied. */
    @SuppressWarnings("CanBeFinal")
    public int timePoint = 0;

    @Override
    public SourceAndConverter<?>[] getTransformedImageMovingToFixed(SourceAndConverter<?>[] img) {
        SourceAndConverter<?>[] out = new SourceAndConverter[img.length];
        for (int idx = 0;idx<img.length;idx++) {
            out[idx] = SourceTransformHelper.createNewTransformedSourceAndConverter(at3d, new SourceAndTimeRange<>(img[idx],timePoint));
        }
        return out;
    }

    @Override
    public RealPointList getTransformedPtsFixedToMoving(RealPointList pts) {
        ArrayList<RealPoint> cvtList = new ArrayList<>();
        for (RealPoint p : pts.ptList) {
            RealPoint pt3d = new RealPoint(3);
            pt3d.setPosition(new double[]{p.getDoublePosition(0), p.getDoublePosition(1),0});
            at3d.inverse().apply(pt3d, pt3d);
            RealPoint cpt = new RealPoint(pt3d.getDoublePosition(0), pt3d.getDoublePosition(1));
            cvtList.add(cpt);
        }
        return new RealPointList(cvtList);
    }

    @Override
    public String getTransform() {
        return ScijavaGsonHelper.getGson(context).toJson(at3d, AffineTransform3D.class);
    }

    @Override
    public void setTransform(String serialized_transform) {
        at3d = ScijavaGsonHelper.getGson(context).fromJson(serialized_transform, AffineTransform3D.class);
        isDone = true;
    }

    /**
     * Opens the {@link AffineEditor} on the current transform. The edition is only offered to the user for
     * registrations annotated as editable, see {@link ch.epfl.biop.registration.plugin.RegistrationTypeProperties}
     * @return true if the user applied the edition
     */
    @Override
    public boolean edit() {
        return editInteractively(at3d, "Edit " + this);
    }

    /**
     * Opens the {@link AffineEditor} on the fixed and moving images and blocks until the user applies or cancels.
     * The gizmo sits at the center of the region given by the parameters px, py, sx and sy, when they are present.
     * @param initial transform the edition starts from
     * @param title title of the editor window
     * @return true if the user applied the edition, whose result is then the transform of this registration
     */
    protected boolean editInteractively(AffineTransform3D initial, String title) {
        AffineTransform3D result = AffineEditor.edit(fimg, mimg, initial, regionOfInterest(), timePoint, title);
        if (result == null) return false;
        at3d = result;
        isDone = true;
        return true;
    }

    /**
     * @return the region {px, py, sx, sy} given in the parameters, or null if one of them is missing
     */
    private double[] regionOfInterest() {
        String[] keys = {"px", "py", "sx", "sy"};
        double[] roi = new double[keys.length];
        for (int i = 0; i < keys.length; i++) {
            String value = getRegistrationParameters().get(keys[i]);
            if (value == null) return null;
            roi[i] = Double.parseDouble(value);
        }
        return roi;
    }

    public RealTransform getTransformAsRealTransform() {
        return at3d.inverse().copy();
    }
}
