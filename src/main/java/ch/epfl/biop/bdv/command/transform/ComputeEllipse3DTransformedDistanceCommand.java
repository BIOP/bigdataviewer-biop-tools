package ch.epfl.biop.bdv.command.transform;

import bdv.util.Elliptical3DTransform;
import ij.IJ;
import net.imglib2.util.LinAlgHelpers;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Compute Ellipsoid Transformed Distance")
public class ComputeEllipse3DTransformedDistanceCommand implements Command {

    @Parameter
    public Elliptical3DTransform e3Dt;

    @Parameter ( stepSize = "0.001")
    public Double pA0 = 1.1;

    @Parameter ( stepSize = "0.001")
    public Double pA1 = 1.0;

    @Parameter ( stepSize = "0.001")
    public Double pA2 = 2.4;

    @Parameter ( stepSize = "0.001")
    public Double pB0 = 1.1;

    @Parameter ( stepSize = "0.001")
    public Double pB1 = 2.2;

    @Parameter ( stepSize = "0.001")
    public Double pB2 = 3.8;

    @Parameter ( min = "1" )
    public int numSteps = 1;

    @Parameter( type = ItemIO.OUTPUT )
    public Double distance;

    @Override
    public void run() {

        final double[] pA = new double[ 3 ];
        pA[ 0 ] = pA0; pA[ 1 ] = pA1; pA[ 2 ] = pA2;
        final double[] pB = new double[ 3 ];
        pB[ 0 ] = pB0; pB[ 1 ] = pB1; pB[ 2 ] = pB2;

        distance = computeDistance( pA, pB, numSteps );
    }

    private double computeDistance( double[] pA, double[] pB, int numSteps )
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

    public static void main( String[] args )
    {
        Context ctx = (Context ) IJ.runPlugIn("org.scijava.Context", "");
        CommandService commandService = ctx.service( CommandService.class );
        commandService.run( ComputeEllipse3DTransformedDistanceCommand.class, true );
    }
}
