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
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.source.SourceHelper;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Register>Warpy>Register Pair - Center Moving Sources On Fixed Sources",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Register Pair - Center Moving Sources On Fixed Sources", weight = 5)
        },
        description = "Applies a translation to center the moving sources over the fixed sources")
public class PairRegistrationCenterCommand extends AbstractPairRegistration2DCommand implements BdvPlaygroundActionCommand {

    @Override
    protected void addRegistrationParameters(Map<String, Object> parameters) {
        AffineTransform3D affineTransform3D = new AffineTransform3D();
        RealPoint centerFixed = SourceHelper.getSourceCenterPoint(registration_pair.getFixedSources()[0], registration_pair.getFixedTimepoint());
        RealPoint centerMoving = SourceHelper.getSourceCenterPoint(registration_pair.getMovingSourcesRegistered()[0], registration_pair.getMovingTimepoint());
        double dx = centerFixed.getDoublePosition(0)-centerMoving.getDoublePosition(0);
        double dy = centerFixed.getDoublePosition(1)-centerMoving.getDoublePosition(1);
        double dz = centerFixed.getDoublePosition(2)-centerMoving.getDoublePosition(2);
        affineTransform3D.translate(dx,dy,dz);
        parameters.put(AffineRegistration.TRANSFORM_KEY, AffineRegistration.affineTransform3DToString(affineTransform3D));
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new AffineRegistration();
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
