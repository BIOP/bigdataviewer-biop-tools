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

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Export Elliptic 3D Transform")
public class Elliptic3DTransformExporterCommand implements BdvPlaygroundActionCommand {

    @Parameter
    Context context;

    @Parameter
    Elliptical3DTransform e3Dt;

    @Parameter(label="Output file", style = "save")
    File file;

    @Override
    public void run() {
        final String json = ScijavaGsonHelper.getGson( context ).toJson( e3Dt, RealTransform.class );

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
            this.file.renameTo( new File( this.file.toString() + ".json" ) );
        }
    }
}
