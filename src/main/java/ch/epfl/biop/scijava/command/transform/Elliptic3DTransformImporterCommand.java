package ch.epfl.biop.scijava.command.transform;

import bdv.util.Elliptical3DTransform;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Import Elliptic 3D Transform")
public class Elliptic3DTransformImporterCommand implements BdvPlaygroundActionCommand {

    @Parameter
    CommandService commandService;

    @Parameter
    Context context;

    @Parameter(label="Json file")
    File file;

    @Parameter(type = ItemIO.OUTPUT)
    Elliptical3DTransform e3Dt;

    @Override
    public void run() {
        e3Dt = readTransformFromFile(file);
        commandService.run(DisplayEllipseFromTransformCommand.class, false, "rMin", 0.9, "rMax", 1.1, "e3Dt", e3Dt);
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
