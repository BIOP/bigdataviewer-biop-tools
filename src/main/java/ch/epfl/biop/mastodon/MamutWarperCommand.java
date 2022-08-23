package ch.epfl.biop.mastodon;

import bdv.util.Elliptical3DTransform;
import ij.IJ;
import ij.gui.ImageCanvas;
import org.mastodon.mamut.MamutAppModel;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.plugin.MamutPluginAppModel;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import sc.fiji.persist.ScijavaGsonHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MamutWarperCommand implements Command {

    @SuppressWarnings( "unused" )
    @Parameter
    private MamutPluginAppModel appModel;

    @Parameter(label = "Select the elliptical transformation file", style = "open")
    File transform_file;

    @Parameter
    Context context;

    @Override
    public void run() {
        Elliptical3DTransform e3Dt;
        try
        {
            e3Dt = ScijavaGsonHelper.getGson( context ).fromJson( new FileReader( transform_file ), Elliptical3DTransform.class );
        } catch ( FileNotFoundException e )
        {
            IJ.error(e.getMessage());
            return;
        }
        MamutAppModel am = appModel.getAppModel();
        Model model = am.getModel();
        ReentrantReadWriteLock.WriteLock lock = model.getGraph().getLock().writeLock();
        try {
            lock.lock();
            // sauve features
            for (Spot spot : model.getGraph().vertices()) {

                e3Dt.apply(spot, spot);
                // radius in covariance
                // EditBehaviours check

            }
            // model.getFeatureModel().getFeatureSpecs() // features spec to add -> look at how this is  - declared and discovered manually
            // restore features
            model.getGraph().notifyGraphChanged();
            model.setUndoPoint();
        } finally {
            lock.unlock();
        }
    }
}
