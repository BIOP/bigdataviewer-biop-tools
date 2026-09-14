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
package ch.epfl.biop.command.register.warpy;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.source.affine.Sift2DAffineRegistration;
import ch.epfl.biop.source.processor.SourcesProcessor;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Register>Warpy>Register Pair 2D - Sift Affine",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Register Pair - Affine SIFT 2D", weight = 6)
        },
        description = "Performs automatic 2D affine registration using SIFT feature matching")

public class PairRegistrationSift2DAffineRegisterCommand extends AbstractPairRegistrationInROI2DCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Transformation model", choices = {"AFFINE", "TRANSLATION"})
    String transformation_model;

    @Parameter(label = "Fixed Channels",
            description = "Channel indices of the fixed image to use for registration (comma separated, e.g., '0' or '0,1')")
    String channels_fixed_csv;

    @Parameter(label = "Moving Channels",
            description = "Channel indices of the moving image to use for registration (comma separated, e.g., '0' or '0,1')")
    String channels_moving_csv;

    @Parameter(label = "Pixel Size (um)",
            description = "Pixel size in micrometers for resampling during registration (larger = faster but less precise)")
    double pixel_size_micrometer = 20;

    @Parameter(label = "Invert Moving",
            description = "When checked, inverts the intensity of the moving image before matching")
    boolean invert_moving;

    @Parameter(label = "Invert Fixed",
            description = "When checked, inverts the intensity of the fixed image before matching")
    boolean invert_fixed;

    @Override
    protected void addRegistrationSpecificParametersExceptRoi(Map<String, Object> parameters) {
        parameters.put(Registration.RESAMPLING_PX_SIZE, pixel_size_micrometer/1000.0);
        parameters.put(Sift2DAffineRegistration.INVERT_MOVING_KEY, invert_moving);
        parameters.put(Sift2DAffineRegistration.INVERT_FIXED_KEY, invert_fixed);
        parameters.put(Sift2DAffineRegistration.TRANSFORMATION_MODEL, transformation_model);
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new Sift2DAffineRegistration();
    }

    @Override
    protected boolean validate() {
        return true;
    }

    @Override
    protected SourcesProcessor getSourcesProcessorFixed() {
        return AbstractPairRegistration2DCommand.getChannelProcessorFromCsv(channels_fixed_csv, registration_pair.getFixedSources().length);
    }

    @Override
    protected SourcesProcessor getSourcesProcessorMoving() {
        return AbstractPairRegistration2DCommand.getChannelProcessorFromCsv(channels_moving_csv, registration_pair.getMovingSourcesOrigin().length);
    }
}
