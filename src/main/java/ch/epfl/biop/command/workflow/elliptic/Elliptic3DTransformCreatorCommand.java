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
package ch.epfl.biop.command.workflow.elliptic;

import bdv.util.Elliptical3DTransform;
import org.scijava.ItemIO;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = BdvPgMenus.RootMenu+"Specialized Workflows>Elliptic Transform>Source - New Elliptic 3D Transform",
        description = "Creates a new elliptical 3D transform with specified radii, rotation, and center")
public class Elliptic3DTransformCreatorCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Radius X",
            style = "format:0.#####E0",
            description = "Radius along the first ellipse axis")
    double radius_x;

    @Parameter(label = "Radius Y",
            style = "format:0.#####E0",
            description = "Radius along the second ellipse axis")
    double radius_y;

    @Parameter(label = "Radius Z",
            style = "format:0.#####E0",
            description = "Radius along the third ellipse axis")
    double radius_z;

    @Parameter(label = "Rotation X",
            style = "format:0.#####E0",
            description = "Euler rotation angle around X axis (radians)")
    double rotation_x;

    @Parameter(label = "Rotation Y",
            style = "format:0.#####E0",
            description = "Euler rotation angle around Y axis (radians)")
    double rotation_y;

    @Parameter(label = "Rotation Z",
            style = "format:0.#####E0",
            description = "Euler rotation angle around Z axis (radians)")
    double rotation_z;

    @Parameter(label = "Center X",
            style = "format:0.#####E0",
            description = "X coordinate of ellipse center")
    double center_x;

    @Parameter(label = "Center Y",
            style = "format:0.#####E0",
            description = "Y coordinate of ellipse center")
    double center_y;

    @Parameter(label = "Center Z",
            style = "format:0.#####E0",
            description = "Z coordinate of ellipse center")
    double center_z;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The created elliptical 3D transform")
    Elliptical3DTransform e3dt;

    @Parameter
    CommandService cs;

    @Override
    public void run() {
        e3dt = new Elliptical3DTransform();
        e3dt.setParameters(
                Elliptical3DTransform.RADIUS_X, radius_x,
                Elliptical3DTransform.RADIUS_Y, radius_y,
                Elliptical3DTransform.RADIUS_Z, radius_z,
                Elliptical3DTransform.ROTATION_X, rotation_x,
                Elliptical3DTransform.ROTATION_Y, rotation_y,
                Elliptical3DTransform.ROTATION_Z, rotation_z,
                Elliptical3DTransform.CENTER_X, center_x,
                Elliptical3DTransform.CENTER_Y, center_y,
                Elliptical3DTransform.CENTER_Z, center_z);

        cs.run(DisplayEllipseFromTransformCommand.class, false, "r_min", 0.9, "r_max", 1.1, "e3dt", e3dt);
    }
}
