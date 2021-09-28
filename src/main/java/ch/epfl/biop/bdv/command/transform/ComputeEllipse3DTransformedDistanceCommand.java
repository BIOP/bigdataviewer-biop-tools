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
    public Elliptical3DTransform e3Dt;

    @Parameter
    public double pA0;

    @Parameter
    public double pA1;

    @Parameter
    public double pA2;

    @Parameter
    public double pB0;

    @Parameter
    public double pB1;

    @Parameter
    public double pB2;

    @Parameter ( min = "1" )
    public int numSteps;

    @Parameter( type = ItemIO.OUTPUT )
    public double distance;

    @Override
    public void run() {

        final double[] pA = { pA0, pA1, pA2 };
        final double[] pB = { pB0, pB1, pB2 };

        distance = computeCurvedDistance( pA, pB, numSteps );
    }

    private double computeCurvedDistance( double[] pA, double[] pB, int numSteps )
    {
        final double[] vAB = new double[ 3 ];
        LinAlgHelpers.subtract( pB, pA, vAB );

        final double[] p0 = new double[ 3 ];
        final double[] p1 = new double[ 3 ];
        final double[] vStep = new double[ 3 ];

        copy( pA, p0 );

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
        double[] pAtransformed = new double[ 3 ];
        double[] pBtransformed = new double[ 3 ];
        e3Dt.apply( pA, pAtransformed );
        e3Dt.apply( pB, pBtransformed );
        return LinAlgHelpers.distance( pAtransformed, pBtransformed );
    }
}
