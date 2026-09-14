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
import net.imglib2.realtransform.RealTransform;
import org.apache.commons.io.FileUtils;
import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = BdvPgMenus.RootMenu+"Specialized Workflows>Elliptic Transform>Source - Export Elliptic 3D Transform",
        description = "Saves an elliptical 3D transform to a JSON file")
public class Elliptic3DTransformExporterCommand implements BdvPlaygroundActionCommand {

    @Parameter
    Context context;

    @Parameter(label = "Elliptical Transform",
            description = "The transform to export")
    Elliptical3DTransform e3dt;

    @Parameter(label = "Output File",
            style = "save",
            description = "Path for the output JSON file")
    File file;

    @Override
    public void run() {
        final String json = ScijavaGsonHelper.getGson( context ).toJson(e3dt, RealTransform.class );

        ensureEndsWithJSON( file );

        writeToFile( json, file );
    }

    private void writeToFile( String json, File file )
    {
        try
        {
            FileUtils.writeStringToFile( file, json, Charset.defaultCharset(), false );
        } catch ( IOException e )
        {
            e.printStackTrace();
        }
    }

    private void ensureEndsWithJSON( File file )
    {
        if ( ! file.toString().endsWith( ".json" ) )
        {
            this.file.renameTo( new File( this.file + ".json" ) );
        }
    }
}
