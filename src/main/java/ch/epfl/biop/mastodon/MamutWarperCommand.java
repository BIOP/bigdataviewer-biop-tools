package ch.epfl.biop.mastodon;

import bdv.util.Elliptical3DTransform;
import ch.epfl.biop.bdv.img.imageplus.ImagePlusHelper;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.realtransform.AffineTransform3D;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MamutWarperCommand implements Command {

    @SuppressWarnings( "unused" )
    @Parameter
    private MamutPluginAppModel appModel;

    @Parameter(label = "Select the image plus exported image")
    ImagePlus image;

    @Parameter(label = "Output cell size")
    double cell_size;

    @Parameter
    Context context;

    @Override
    public void run() {

        AffineTransform3D matrix = ImagePlusHelper.getMatrixFromImagePlus(image);

        Elliptical3DTransform e3Dt = getEllipticalTransformFromImagePlus(image);

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

    private static Elliptical3DTransform getEllipticalTransformFromImagePlus(Context context, ImagePlus image) {

        Elliptical3DTransform e3Dt;
/*        try
        {
            e3Dt = ScijavaGsonHelper.getGson( context ).fromJson( new FileReader( transform_file ), Elliptical3DTransform.class );
        } catch ( FileNotFoundException e )
        {
            IJ.error(e.getMessage());
            return;
        }
        return e3Dt;*/
        AffineTransform3D at3D;

        if (image.getInfoProperty() == null) throw new UnsupportedOperationException("No elliptic transform found in file");

        if (image.getInfoProperty() != null) {

            Pattern pattern = Pattern.compile("(3d-affine: \\()(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.+),(.*)\\)");
            Matcher matcher = pattern.matcher(imp.getInfoProperty());
            if (matcher.find()) {
                double[] m = new double[12];

                for(int i = 0; i < 12; ++i) {
                    m[i] = Double.parseDouble(matcher.group(i + 2));
                }

                at3D.set(m);
                return at3D;
            }
        }

        if (imp.getCalibration() != null) {
            at3D = new AffineTransform3D();
            at3D.scale(imp.getCalibration().pixelWidth, imp.getCalibration().pixelHeight, imp.getCalibration().pixelDepth);
            at3D.translate(new double[]{imp.getCalibration().xOrigin * imp.getCalibration().pixelWidth, imp.getCalibration().yOrigin * imp.getCalibration().pixelHeight, imp.getCalibration().zOrigin * imp.getCalibration().pixelDepth});
            return at3D;
        } else {
            return new AffineTransform3D();
        }

    }

    private static void covarianceFromRadiusSquared( final double rsqu, final double[][] cov )
    {
        for( int row = 0; row < 3; ++row )
            for( int col = 0; col < 3; ++col )
                cov[ row ][ col ] = ( row == col ) ? rsqu : 0;
    }

    ("ellipse_params":)((?s).*)(})
}
