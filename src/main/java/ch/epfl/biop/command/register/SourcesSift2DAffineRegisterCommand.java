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

import ch.epfl.biop.source.register.SIFTRegister;
import mpicbg.imagefeatures.FloatArray2DSIFT;
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
    //    menuPath = BdvPgMenus.RootMenu+"Source>Register>Obsolete>Register Sources with SIFT (Affine, 2D)",
        description = "Performs an affine registration in 2D between 2 sources. Low level command which\n"+
                      "requires many parameters. For more user friendly command, use wizards instead.\n"+
                      "Outputs the transform to apply to the moving source.")
public class SourcesSift2DAffineRegisterCommand extends Abstract2DRegistrationInRectangleCommand implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(SourcesSift2DAffineRegisterCommand.class);

    @Parameter(label = "Transformation model", choices = {"AFFINE", "TRANSLATION"})
    String transformation_model;

    @Parameter(label = "Invert Moving",
            description = "When checked, inverts the intensity of the moving image for matching")
    boolean invert_moving;

    @Parameter(label = "Invert Fixed",
            description = "When checked, inverts the intensity of the fixed image for matching")
    boolean invert_fixed;

    @Parameter(type = ItemIO.OUTPUT,
            description = "Whether the registration completed successfully")
    boolean success;

    @Override
    public void run() {
        FloatArray2DSIFT.Param param = new FloatArray2DSIFT.Param();
        param.maxOctaveSize = 2048;

        SIFTRegister.MODEL model;

        switch (transformation_model) {
            case "AFFINE": model = SIFTRegister.MODEL.AFFINE;break;
            case "TRANSLATION": model = SIFTRegister.MODEL.TRANSLATION; break;
            default:
                throw new RuntimeException("Unknown transformation model "+transformation_model);
        }

        SIFTRegister reg = new SIFTRegister(
                sources_fixed, level_fixed_source, tp_fixed,invert_fixed,
                sources_moving, level_moving_source, tp_moving,invert_moving,
                px_size_in_current_unit,
                px,py,pz,sx,sy,
                param,
                0.92f,
                25.0f,
                0.05f,
                7,
                model
                );

        reg.setInterpolate(interpolate);

        success = reg.run();

        if (success) {
            at3d = reg.getAffineTransform();
        } else {
            logger.error("Error during registration: "+reg.getErrorMessage());
        }
    }

}
