package ch.epfl.biop.bdv.command.transform;

import bdv.util.Elliptical3DTransform;
import ij.IJ;
import net.imglib2.util.LinAlgHelpers;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Compute Ellipsoid Transformed Distance")
public class ComputeEllipse3DTransformedDistanceCommand implements Command {

    @Parameter
    Elliptical3DTransform e3Dt;

    @Parameter
    double pA0;

    @Parameter
    double pA1;

    @Parameter
    double pA2;

    @Parameter
    double pB0;

    @Parameter
    double pB1;

    @Parameter
    double pB2;

    @Parameter( type = ItemIO.OUTPUT )
    double straightDistance;

    @Parameter( type = ItemIO.OUTPUT )
    double curvedDistance;

    private double[] pAtransformed;
    private double[] pBtransformed;

    @Override
    public void run() {

        pAtransformed = new double[ 3 ];
        pBtransformed = new double[ 3 ];
        final double[] pA = { pA0, pA1, pA2 };
        final double[] pB = { pB0, pB1, pB2 };

        straightDistance = distance( pA, pB );
        IJ.log( "Straight distance: " + straightDistance );

        curvedDistance = computeCurvedDistance( pA, pB );
        IJ.log( "Curved distance: " + curvedDistance );
    }

    private double computeCurvedDistance( double[] pA, double[] pB )
    {
        final double[] vAB = new double[ 3 ];
        LinAlgHelpers.subtract( pB, pA, vAB );

        final double[] p0 = new double[ 3 ];
        final double[] p1 = new double[ 3 ];
        final double[] vStep = new double[ 3 ];

        copy( pA, p0 );

        final int numSteps = 100;
        LinAlgHelpers.scale( vAB, 1.0 / numSteps, vStep );

        double curvedDistance = 0.0;
        for ( int i = 0; i < numSteps; i++ )
        {
            LinAlgHelpers.add( p0, vStep, p1 );
            curvedDistance += distance( p0, p1 );
            copy( p1, p0 );
        }

        return curvedDistance;
    }

    private void copy( double[] source, double[] target )
    {
        for ( int d = 0; d < source.length; d++ )
            target[ d ] = source[ d ];
    }

    private double distance( double[] pA, double[] pB )
    {
        e3Dt.applyInverse( pA, pAtransformed );
        e3Dt.applyInverse( pB, pBtransformed );
        return LinAlgHelpers.distance( pAtransformed, pBtransformed );
    }
}
