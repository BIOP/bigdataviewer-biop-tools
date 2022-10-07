package ch.epfl.biop.mastodon;

import bdv.util.Elliptical3DTransform;
import ij.IJ;
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

    @Parameter(label = "Output cell size")
    double cell_size;

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
        double[][] cov = new double[3][3];
        covarianceFromRadiusSquared(cell_size, cov);
        try {
            lock.lock();
            // sauve features
            for (Spot spot : model.getGraph().vertices()) {

                e3Dt.inverse().apply(spot, spot);
                // radius in covariance
                spot.setCovariance(cov);
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

    private static void covarianceFromRadiusSquared( final double rsqu, final double[][] cov )
    {
        for( int row = 0; row < 3; ++row )
            for( int col = 0; col < 3; ++col )
                cov[ row ][ col ] = ( row == col ) ? rsqu : 0;
    }
}
