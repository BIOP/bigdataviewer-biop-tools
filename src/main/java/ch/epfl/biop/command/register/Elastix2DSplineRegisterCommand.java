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

import ch.epfl.biop.source.register.Elastix2DSplineRegister;
import net.imglib2.realtransform.RealTransform;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

// Legacy command, kept for backward compatibility with existing scripts.
// Declared as a plain Command rather than a BdvPlaygroundActionCommand: it has no menu
// path, and the BDV Playground SourceService cannot register a menu-less action.
@Plugin(type = Command.class,
    //    menuPath = BdvPgMenus.RootMenu+"Source>Register>Obsolete>Register Sources with Elastix (Spline, 2D)",
        description = "Performs B-spline deformable registration in 2D between two sources using Elastix")
public class Elastix2DSplineRegisterCommand extends AbstractElastix2DRegistrationInRectangleCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Control Points X",
            description = "Number of B-spline control points along the X axis")
    int num_ctrl_points_x;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The forward spline transformation")
    RealTransform rt;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The inverse spline transformation")
    RealTransform rt_inverse;

    @Parameter
    Context ctx;

    @Parameter(type = ItemIO.OUTPUT,
            description = "Whether the registration completed successfully")
    boolean success;

    @Override
    public void run() {
        Elastix2DSplineRegister reg = new Elastix2DSplineRegister(
                sources_fixed, level_fixed_source, tp_fixed,
                sources_moving, level_moving_source, tp_moving,
                num_ctrl_points_x,
                px_size_in_current_unit,
                px,py,pz,sx,sy,
                max_iteration_per_scale,
                show_image_registration);

        reg.setInterpolate(interpolate);

        success = reg.run();

        //registeredSource = reg.getRegisteredSac();
        rt = reg.getRealTransform();
        rt_inverse = reg.getRealTransformInverse();
    }

}
