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
package ch.epfl.biop.command.register;

import ch.epfl.biop.source.register.Elastix2DAffineRegister;
import ch.epfl.biop.wrappers.elastix.RegParamAffine_Fast;
import ch.epfl.biop.wrappers.elastix.RegisterHelper;
import ch.epfl.biop.wrappers.elastix.RegistrationParameters;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

/**
 * This command automatically computes the number of scales needed for registration
 * It resamples the original sources in order to allow for a registration based on a specific
 * region and not on the whole image. This also allows to register landmark regions
 * on big images. See {@link Sources2DRegisterCommand}
 */

// Legacy command, kept for backward compatibility with existing scripts.
// Declared as a plain Command rather than a BdvPlaygroundActionCommand: it has no menu
// path, and the BDV Playground SourceService cannot register a menu-less action.
@Plugin(type = Command.class,
        //menuPath = BdvPgMenus.RootMenu+"Source>Register>Register Sources with Elastix (Affine, 2D)",
        description = "Performs an affine registration in 2D between 2 sources. Low level command which\n"+
                      "requires many parameters. For more user friendly command, use wizards instead.\n"+
                      "Outputs the transform to apply to the moving source.")
public class Elastix2DAffineRegisterCommand extends AbstractElastix2DRegistrationInRectangleCommand implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(Elastix2DAffineRegisterCommand.class);

    @Parameter
    Context ctx;

    @Parameter(type = ItemIO.OUTPUT)
    boolean success;

    @Override
    public void run() {
        RegisterHelper rh = new RegisterHelper();
        if (verbose) {
            rh.verbose();
        }

        RegistrationParameters rp;

        if (sources_fixed.length>1) {
            if (sources_fixed.length== sources_moving.length) {
                RegistrationParameters[] rps = new RegistrationParameters[sources_fixed.length];
                for (int iCh = 0; iCh< sources_fixed.length; iCh++) {
                    rps[iCh] = getRegistrationParameters();
                }
                rp = RegistrationParameters.combineRegistrationParameters(rps);
            } else {
                System.err.println("Cannot perform multichannel registration : non identical number of channels between moving and fixed sources.");
                rp = getRegistrationParameters();
            }
        } else {
            rp = getRegistrationParameters();
        }

        rh.addTransform(rp);

        Elastix2DAffineRegister reg = new Elastix2DAffineRegister(
                sources_fixed, level_fixed_source, tp_fixed,
                sources_moving, level_moving_source, tp_moving,
                rh,
                px_size_in_current_unit,
                px,py,pz,sx,sy,
                show_image_registration);
        reg.setInterpolate(interpolate);

        success = reg.run();

        if (success) {
            at3d = reg.getAffineTransform();
        } else {
            logger.error("Error during registration");
        }
    }

    private RegistrationParameters getRegistrationParameters() {
        RegistrationParameters rp = new RegParamAffine_Fast(); //

        rp.AutomaticScalesEstimation = false;
        if (automatic_transform_initialization) {
            rp.AutomaticTransformInitialization = true;
            rp.AutomaticTransformInitializationMethod = "CenterOfGravity";
        } else {
            rp.AutomaticTransformInitialization = false;
        }

        double maxSize = Math.min(sx/ px_size_in_current_unit,sy/ px_size_in_current_unit);

        int nScales = 0;

        while (Math.pow(2,nScales)<maxSize) {
            nScales++;
        }

        int nScalesSkipped = 0;

        while (Math.pow(2,nScalesSkipped)< min_image_size_pix) {
            nScalesSkipped++;
        }

        rp.NumberOfResolutions = Math.max(1,nScales-nScalesSkipped); // Starts with 2^nScalesSkipped pixels

        rp.BSplineInterpolationOrder = 1;
        rp.MaximumNumberOfIterations = max_iteration_per_scale;

        rp.ImagePyramidSchedule = new Integer[2*rp.NumberOfResolutions];
        for (int scale = 0; scale < rp.NumberOfResolutions ; scale++) {
            rp.ImagePyramidSchedule[2*scale] = (int) Math.pow(2, rp.NumberOfResolutions-scale-1);
            rp.ImagePyramidSchedule[2*scale+1] = (int) Math.pow(2, rp.NumberOfResolutions-scale-1);
        }
        return rp;
    }
}
