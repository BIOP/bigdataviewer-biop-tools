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
import ch.epfl.biop.registration.source.affine.AffineRegistration;
import ch.epfl.biop.source.processor.SourcesIdentity;
import ch.epfl.biop.source.processor.SourcesProcessor;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Register>Warpy>Register Pair - Rotate",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Register Pair - Rotate", weight = 3)
        },
        description = "Applies a rotation transformation to the moving sources around X, Y, or Z axis")
public class PairRegistrationRotateCommand extends AbstractPairRegistration2DCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Angle",
            description = "Rotation angle in degrees",
            choices = {"90", "180", "270"})
    String angle = "90";

    @Parameter(label = "Axis",
            description = "Axis around which to rotate the moving sources",
            choices = {"X", "Y", "Z"})
    String axis = "Z";

    @Parameter(label = "Keep Center",
            description = "When checked, maintains the center position of the moving sources unchanged")
    boolean keep_center = true;

    @Override
    protected void addRegistrationParameters(Map<String, Object> parameters) {
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.identity();

        // Determine quarter turns (90° increments)
        int quarterTurn = Integer.parseInt(angle) / 90;

        // Apply rotation transformation
        int axisIndex;
        switch (axis) {
            case "X":
                axisIndex = 0;
                break;
            case "Y":
                axisIndex = 1;
                break;
            case "Z":
                axisIndex = 2;
                break;
            default:
                axisIndex = 2;
        }

        at3D.rotate(axisIndex, ((double) quarterTurn) * Math.PI / 2.0);

        if (keep_center) {
            // Maintain center of moving sources constant
            SourceAndConverter<?> movingSac = registration_pair.getMovingSourcesRegistered()[0];
            int timepoint = registration_pair.getMovingTimepoint();

            AffineTransform3D sourceTransform = new AffineTransform3D();
            movingSac.getSpimSource().getSourceTransform(timepoint, 0, sourceTransform);

            long[] dims = new long[3];
            movingSac.getSpimSource().getSource(timepoint, 0).dimensions(dims);

            RealPoint ptCenterPixel = new RealPoint(
                (dims[0] - 1.0) / 2.0,
                (dims[1] - 1.0) / 2.0,
                (dims[2] - 1.0) / 2.0
            );

            RealPoint ptCenterGlobalBefore = new RealPoint(3);
            sourceTransform.apply(ptCenterPixel, ptCenterGlobalBefore);

            RealPoint ptCenterGlobalAfter = new RealPoint(3);
            at3D.apply(ptCenterGlobalBefore, ptCenterGlobalAfter);

            // Adjust translation to maintain center
            double[] m = at3D.getRowPackedCopy();
            m[3] -= ptCenterGlobalAfter.getDoublePosition(0) - ptCenterGlobalBefore.getDoublePosition(0);
            m[7] -= ptCenterGlobalAfter.getDoublePosition(1) - ptCenterGlobalBefore.getDoublePosition(1);
            m[11] -= ptCenterGlobalAfter.getDoublePosition(2) - ptCenterGlobalBefore.getDoublePosition(2);
            at3D.set(m);
        }

        parameters.put(AffineRegistration.TRANSFORM_KEY, AffineRegistration.affineTransform3DToString(at3D));
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        AffineRegistration reg = new AffineRegistration();
        reg.setRegistrationName("Rotate " + angle + "° " + axis);
        return reg;
    }

    @Override
    protected boolean validate() {
        return true;
    }

    @Override
    protected SourcesProcessor getSourcesProcessorFixed() {
        return new SourcesIdentity();
    }

    @Override
    protected SourcesProcessor getSourcesProcessorMoving() {
        return new SourcesIdentity();
    }
}
