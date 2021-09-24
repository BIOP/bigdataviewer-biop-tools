package ch.epfl.biop.bdv.command.transform;

import bdv.img.WarpedSource;
import bdv.util.Elliptical3DTransform;
import bdv.util.Procedural3DImageShort;
import bdv.util.RealRandomAccessibleIntervalSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ij.IJ;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.LinAlgHelpers;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAdjuster;

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
    double distance;

    @Override
    public void run() {

        final double[] pAtransformed = new double[ 3 ];
        e3Dt.applyInverse( new double[]{ pA0, pA1, pA2 }, pAtransformed );
        final double[] pBtransformed = new double[ 3 ];
        e3Dt.applyInverse( new double[]{ pB0, pB1, pB2 }, pBtransformed );
        distance = LinAlgHelpers.distance( pAtransformed, pBtransformed );
        IJ.log( "Distance: " + distance );

    }
}
