package ch.epfl.biop.bdv.sampleimage;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvSource;
import net.imglib2.*;
import net.imglib2.algorithm.util.Grids;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.neighborsearch.NearestNeighborSearchInterpolatorFactory;
import net.imglib2.neighborsearch.NearestNeighborSearch;
import net.imglib2.neighborsearch.NearestNeighborSearchOnKDTree;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.Random;


@Plugin(type= Command.class, initializer = "init", menuPath = "Plugins>BIOP>BDV>Samples>Voronoi Label 3D")
public class VoronoiLabel3D  implements Command{
    @Parameter(label = "Create new BigDataViewer window")
    public boolean createNewWindow;

    // ItemIO.BOTH required because it can be modified in case of appending new data to BDV (-> requires INPUT), or created (-> requires OUTPUT)
    @Parameter(label = "BigDataViewer Frame", type = ItemIO.BOTH, required = false)
    public BdvHandle bdv_h;

    @Parameter
    public boolean computeImageBeforeDisplay = false;

    @Parameter
    public int sx = 256;

    @Parameter
    public int sy = 256;

    @Parameter
    public int sz = 256;

    @Parameter(label = "Number of Labels")
    public int numLabels = 256;

    public void run() {
        BdvOptions options = BdvOptions.options();
        if (createNewWindow == false && bdv_h!=null) {
            options.addTo(bdv_h).is2D();
        }

        final RandomAccessibleInterval<FloatType> labelImage = getTestLabelImage(new long[] { sx, sy, sz }, numLabels, computeImageBeforeDisplay);

        final BdvSource source = BdvFunctions.show(labelImage,"Voronoi Label Image",  options);
        source.setColor(new ARGBType(0xFF00FF00));
        source.setDisplayRange(0,1);

        bdv_h = source.getBdvHandle();
    }

    //------------------------------------- METHODS TO CREATE TEST LABEL IMAGE

    public static RandomAccessibleInterval< FloatType > getTestLabelImage(final long[] imgTestSize, int numPts, boolean copyImg) {

        // the interval in which to create random points
        FinalInterval interval = new FinalInterval( imgTestSize );

        // create an IterableRealInterval
        IterableRealInterval< FloatType > realInterval = createRandomPoints( interval, numPts );

        // using nearest neighbor search we will be able to return a value an any position in space
        NearestNeighborSearch< FloatType > search =
                new NearestNeighborSearchOnKDTree<>(
                        new KDTree<>( realInterval ) );

        // make it into RealRandomAccessible using nearest neighbor search
        RealRandomAccessible< FloatType > realRandomAccessible =
                Views.interpolate( search, new NearestNeighborSearchInterpolatorFactory< FloatType >() );

        // convert it into a RandomAccessible which can be displayed
        RandomAccessible< FloatType > randomAccessible = Views.raster( realRandomAccessible );

        // set the initial interval as area to view
        RandomAccessibleInterval< FloatType > labelImage = Views.interval( randomAccessible, interval );

        if (copyImg) {
            final RandomAccessibleInterval< FloatType > labelImageCopy = new ArrayImgFactory( Util.getTypeFromInterval( labelImage ) ).create( labelImage );

            // Image copied to avoid computing it on the fly
            // https://github.com/imglib/imglib2-algorithm/blob/47cd6ed5c97cca4b316c92d4d3260086a335544d/src/main/java/net/imglib2/algorithm/util/Grids.java#L221 used for parallel copy

            Grids.collectAllContainedIntervals(imgTestSize, new int[]{64, 64, 64}).stream().forEach(blockinterval -> {
                copy(labelImage, Views.interval(labelImageCopy, blockinterval));
            });

            // Alternative non parallel copy
            //LoopBuilder.setImages(labelImage, labelImageCopy).forEachPixel(Type::set);
            return labelImageCopy;

        } else {

            return labelImage;
        }
    }

    /**
     * Copy from a source that is just RandomAccessible to an IterableInterval. Latter one defines
     * size and location of the copy operation. It will query the same pixel locations of the
     * IterableInterval in the RandomAccessible. It is up to the developer to ensure that these
     * coordinates match.
     *
     * Note that both, input and output could be Views, Img or anything that implements
     * those interfaces.
     *
     * @param source - a RandomAccess as source that can be infinite
     * @param target - an IterableInterval as target
     */
    public static < T extends Type< T >> void copy(final RandomAccessible< T > source,
                                                   final IterableInterval< T > target )
    {
        // create a cursor that automatically localizes itself on every move
        Cursor< T > targetCursor = target.localizingCursor();
        RandomAccess< T > sourceRandomAccess = source.randomAccess();

        // iterate over the input cursor
        while ( targetCursor.hasNext())
        {
            // move input cursor forward
            targetCursor.fwd();

            // set the output cursor to the position of the input cursor
            sourceRandomAccess.setPosition( targetCursor );

            // set the value of this pixel of the output image, every Type supports T.set( T type )
            targetCursor.get().set( sourceRandomAccess.get() );
        }

    }

    /**
     * Create a number of n-dimensional random points in a certain interval
     * having a random intensity 0...1
     *
     * @param interval - the interval in which points are created
     * @param numPoints - the amount of points
     *
     * @return a RealPointSampleList (which is an IterableRealInterval)
     */
    public static RealPointSampleList< FloatType > createRandomPoints(
            RealInterval interval, int numPoints )
    {
        // the number of dimensions
        int numDimensions = interval.numDimensions();

        // a random number generator
        Random rnd = new Random( 2001);//System.currentTimeMillis() );

        // a list of Samples with coordinates
        RealPointSampleList< FloatType > elements =
                new RealPointSampleList<>( numDimensions );

        for ( int i = 0; i < numPoints; ++i )
        {
            RealPoint point = new RealPoint( numDimensions );

            for ( int d = 0; d < numDimensions; ++d )
                point.setPosition( rnd.nextDouble() *
                        ( interval.realMax( d ) - interval.realMin( d ) ) + interval.realMin( d ), d );

            // add a new element with a random intensity in the range 0...1
            elements.add( point, new FloatType( rnd.nextFloat() ) );
        }

        return elements;
    }

}
