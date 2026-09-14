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
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = BdvPgMenus.RootMenu+"Specialized Workflows>Elliptic Transform>Source - Import Elliptic 3D Transform",
        description = "Loads an elliptical 3D transform from a JSON file")
public class Elliptic3DTransformImporterCommand implements BdvPlaygroundActionCommand {

    @Parameter
    CommandService commandService;

    @Parameter
    Context context;

    @Parameter(label = "JSON File",
            description = "Path to the JSON file containing the transform")
    File file;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The loaded elliptical 3D transform")
    Elliptical3DTransform e3dt;

    @Override
    public void run() {
        e3dt = readTransformFromFile(file);
        commandService.run(DisplayEllipseFromTransformCommand.class, false, "r_min", 0.9, "r_max", 1.1, "e3dt", e3dt);
    }

    private Elliptical3DTransform readTransformFromFile(File file)
    {
        try
        {
           return ScijavaGsonHelper.getGson( context ).fromJson( new FileReader( file ), Elliptical3DTransform.class );
        } catch ( FileNotFoundException e )
        {
            e.printStackTrace();
        }
        return null;
    }
}
