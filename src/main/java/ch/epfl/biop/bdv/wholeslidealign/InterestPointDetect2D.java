package ch.epfl.biop.bdv.wholeslidealign;
import bdv.util.BdvFunctions;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imagej.ops.OpService;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.dog.DogDetection;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;


// Source : https://forum.image.sc/t/detecting-cells-with-difference-of-gaussian-using-imglib2/5191/2

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Registration>Detect Interest Points")
public class InterestPointDetect2D implements Command {

    @Parameter
    AbstractSpimData sd;

    @Parameter
    String datasetName;

    @Parameter
    OpService ops;

    @Parameter
    Integer viewSetupId;

    @Parameter
    Integer timePoint;

    @Parameter
    Integer mipMapLevel;

    @Parameter
    double cell = 5.0;

    @Parameter
    double min_peak = 40.0;

    @Parameter
    int zLocation;

    public void run() {
/*
        final HashMap<ViewId, List<InterestPoint>> points = new HashMap<>();

        List<InterestPoint> ips = new ArrayList<>();

        // DETECTION PEAKS

        BasicSetupImgLoader il = sd.getSequenceDescription().getImgLoader().getSetupImgLoader(viewSetupId);

        RandomAccessibleInterval rai;

        if (il instanceof MultiResolutionSetupImgLoader) {
            rai = ((MultiResolutionSetupImgLoader) il).getImage(timePoint, mipMapLevel);
        } else {
            rai = il.getImage(timePoint);
        }

        rai = Views.hyperSlice(rai,2,zLocation); // What a nightmare

        ExtendedRandomAccessibleInterval extended = (ExtendedRandomAccessibleInterval) ops.run("transform.extendZeroView", rai);

        ImageJFunctions.show(rai,"rai");

        DogDetection dog = new DogDetection(extended, rai,
                new double[]{0.5,0.5},
        cell / 2.0, cell,
                DogDetection.ExtremaType.MINIMA,
                min_peak, false,
                new DoubleType());



        ArrayList<Point> ipts = dog.getPeaks();

        for (Point pt:ipts) {
            ips.add(new InterestPoint(0, new double[]{pt.getDoublePosition(0),pt.getDoublePosition(1),zLocation}));
        }

        System.out.println("Number of points detected = "+ ipts.size());

        // End of peak detection

        points.put(new ViewId(0,3),ips);

        if (sd instanceof SpimData2) {
            boolean interestPointsAdded = addInterestPoints( (SpimData2) sd,  "ipt2d", points, null );

            if (!interestPointsAdded) {
                System.err.println("Interest points not added");
            }

            try {
                for ( final ViewId viewId : ((SpimData2)sd).getViewInterestPoints().getViewInterestPoints().keySet() )
                {
                    final ViewInterestPointLists vipl =  ((SpimData2)sd).getViewInterestPoints().getViewInterestPoints().get( viewId );
                    for ( final String label : vipl.getHashMap().keySet() )
                    {
                        final InterestPointList ipl = vipl.getHashMap().get( label );

                        // save if interestpoints were loaded or created, potentially modified
                        ipl.saveInterestPoints( false );
                        ipl.saveCorrespondingInterestPoints( false );
                    }
                }

                new XmlIoSpimData2("").save((SpimData2) sd, new File(sd.getBasePath(),datasetName).getAbsolutePath());
            } catch (SpimDataException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("Dataset should be open as a Spim2Dataset");
        }*/
    }


    /**
     * Add interest points.
     *
     * @param label the label
     * @param points the points
     * @param parameters the parameters
     * @return the true if successful, false if interest points cannot be saved
     */
   /*
    // final SpimData2 data
    public static boolean addInterestPoints(SpimData2 sd2, String label, final HashMap< ViewId, List< InterestPoint > > points, final String parameters )
    {
    for ( final ViewId viewId : points.keySet() )
        {
            final InterestPointList list =
                    new InterestPointList(
                            sd2.getBasePath(),
                            new File(
                                    "interestpoints", "tpId_" + viewId.getTimePointId() +
                                    "_viewSetupId_" + viewId.getViewSetupId() + "." + label ) );

            if ( parameters != null )
                list.setParameters( parameters );
            else
                list.setParameters( "" );

            list.setInterestPoints( points.get( viewId ) );
            list.setCorrespondingInterestPoints( new ArrayList<>() );

            final ViewInterestPointLists vipl = sd2.getViewInterestPoints().getViewInterestPointLists( viewId );
            vipl.addInterestPointList( label, list );
        }

        return true;
    }
    */

}
/**
 # @OpService ops
 # @ImgPlus img

 # A script to find cells by difference of Gaussian using imglib2.
 # Uses as an example the "first-instar-brain.tif" RGB stack availalable
 # from Fiji's "Open Samples" menu.

 from net.imglib2.algorithm.dog import DogDetection
 from net.imglib2.type.numeric.real import DoubleType

 # Extract the red channel
 interval = ops.run("create.img", [img.dimension(0), img.dimension(1), 1, img.dimension(3)], img.firstElement(), img.factory())
 red = ops.run("transform.crop", img, interval)

 # Create a variable of the correct type (UnsignedByteType) for the value-extended view
 extended = ops.run("transform.extendZeroView", red)

 # Run the difference of Gaussian
 cell = 5.0 # microns in diameter
 min_peak = 40.0 # min intensity for a peak to be considered

 dog = DogDetection(extended, red,
 [img.averageScale(0), img.averageScale(1), img.averageScale(3)],
 cell / 2, cell,
 DogDetection.ExtremaType.MINIMA,
 min_peak, False,
 DoubleType())

 peaks = dog.getPeaks()

 # Prints 43
 print len(peaks)
 print peaks
 */