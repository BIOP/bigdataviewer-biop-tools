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
import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

import java.util.List;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Sources>Register>Create registration pair (Advanced)",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Create Registration Pair (Specify Timepoint)", weight = 1.1)
        },
        description = "Creates a new registration pair from fixed and moving sources for the Warpy workflowq, specifying timepoint")
public class PairRegistrationCreateAdvancedCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Fixed Source(s)",
            description = "The reference source(s) that will remain stationary during registration",
            style = "sorted")
    SourceAndConverter<?>[] fixed_sources;

    @Parameter
    Integer timepoint_fixed = 0;

    @Parameter(label = "Moving Source(s)",
            description = "The source(s) to be registered and aligned to the fixed source(s)",
            style = "sorted")
    SourceAndConverter<?>[] moving_sources;

    @Parameter
    Integer timepoint_moving = 0;

    @Parameter(label = "Registration Name",
            description = "A unique name to identify this registration pair")
    String registration_name;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Registration Pair",
            description = "The created registration pair object")
    RegistrationPair registration_pair;

    @Parameter
    ObjectService objectService;

    @Override
    public void run() {
        List<RegistrationPair> allRegistrations = objectService.getObjects(RegistrationPair.class);

        if (allRegistrations.stream().anyMatch(rps -> rps.getName().equals(registration_name))) {
            System.err.println("A registration sequence named "+registration_name+" already exists! Please name it differently or delete the existing one.");
            return;
        }

        registration_pair = new RegistrationPair(fixed_sources, timepoint_fixed, moving_sources, timepoint_moving, registration_name, true);
        objectService.addObject(registration_pair);

    }
}

