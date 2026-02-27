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
