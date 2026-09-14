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
package ch.epfl.biop.registration.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.plugin.IRegistrationPlugin;
import org.scijava.Context;
import org.scijava.plugin.Parameter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract public class SourceRegistration implements IRegistrationPlugin {

    protected SourceAndConverter<?>[] fimg;

    protected SourceAndConverter<?>[] mimg;

    protected SourceAndConverter<?>[] fimg_mask;

    protected SourceAndConverter<?>[] mimg_mask;

    protected int timePoint = 0;

    @Parameter
    protected Context context;

    @Override
    public void setFixedImage(SourceAndConverter<?>[] fimg) {
        this.fimg = fimg;
    }

    @Override
    public void setMovingImage(SourceAndConverter<?>[] mimg) {
        this.mimg = mimg;
    }

    @Override
    public void setFixedMask(SourceAndConverter<?>[] fimg) {
        this.fimg_mask = fimg;
    }

    @Override
    public void setMovingMask(SourceAndConverter<?>[] mimg) {
        this.mimg_mask = mimg;
    }

    @Override
    public void setTimePoint(int timePoint) {
        this.timePoint = timePoint;
    }

    /**
     * Is called just after the Registration object creation to pass
     * the current scijava context
     * is adopted by all registrations
     * @param context the SciJava context to use for this registration
     */
    public void setScijavaContext(Context context) {
        this.context = context;
    }

    protected boolean isDone = false;

    @Override
    public boolean isRegistrationDone() {
        return isDone;
    }

    @Override
    public void resetRegistration() {
        isDone = false;
    }

    protected Map<String, String> parameters = new HashMap<>();

    @Override
    public Map<String, String> getRegistrationParameters() {
        if (parameters!=null) {
            return parameters;
        } else {
            return new HashMap<>();
        }
    }

    @Override
    public void setRegistrationParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    protected static void addToFlatParameters(List<Object> flatParameters, Object... args) {
        flatParameters.addAll(Arrays.asList(args));
    }

}
