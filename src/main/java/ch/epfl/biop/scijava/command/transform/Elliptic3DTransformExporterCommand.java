package ch.epfl.biop.scijava.command.transform;

import bdv.util.Elliptical3DTransform;
import net.imglib2.realtransform.RealTransform;
import org.apache.commons.io.FileUtils;
import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Export Elliptic 3D Transform",
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
