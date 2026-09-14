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
import ch.epfl.biop.registration.plugin.IRegistrationPlugin;
import ch.epfl.biop.registration.plugin.RegistrationTypeProperties;
import com.google.gson.Gson;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registration class for applying affine transformations programmatically.
 * Allows creating and applying affine transforms conveniently.
 * The transform can be edited afterwards with the {@link AffineEditor}.
 */
@Plugin(type = IRegistrationPlugin.class)
@RegistrationTypeProperties(
        isManual = false,
        isEditable = true)
public class AffineRegistration extends AffineTransformSourceRegistration {

    /** Logger for this class. */
    protected static Logger logger = LoggerFactory.getLogger(AffineRegistration.class);

    /** Parameter key for the affine transform. */
    public final static String TRANSFORM_KEY = "transform";

    @Override
    public void setFixedImage(SourceAndConverter<?>[] fimg) {
        super.setFixedImage(fimg);
    }

    @Override
    public void setMovingImage(SourceAndConverter<?>[] mimg) {
        super.setMovingImage(mimg);
    }

    /**
     * Converts an AffineTransform3D to a JSON string representation.
     *
     * @param transform the affine transform to convert
     * @return JSON string representation of the transform
     */
    public static String affineTransform3DToString(AffineTransform3D transform) {
        return new Gson().toJson(transform.getRowPackedCopy());
    }

    /**
     * Converts a JSON string back to an AffineTransform3D.
     *
     * @param string JSON string representation of the transform
     * @return the reconstructed AffineTransform3D
     */
    public static AffineTransform3D stringToAffineTransform3D(String string) {
        AffineTransform3D transform3D = new AffineTransform3D();
        double[] matrix = new Gson().fromJson(string, double[].class);
        transform3D.set(matrix);
        return transform3D;
    }

    @Override
    public boolean register() {
        at3d = stringToAffineTransform3D(parameters.get(TRANSFORM_KEY));
        isDone = true;
        return true;
    }

    @Override
    public void abort() {

    }

    String name = "Affine";

    @Override
    public void setRegistrationName(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

}
