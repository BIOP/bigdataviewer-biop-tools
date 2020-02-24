package ch.epfl.biop.scijava.command;

import bdv.img.WarpedSource;
import bdv.util.*;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterUtils;


@Plugin(type = Command.class, menuPath = "BigDataViewer>Sources>Transform>Create Ellipsoid Source")
public class DisplayEllipseFromTransformCommand implements Command {

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter sac_out;

    @Parameter(type = ItemIO.BOTH)
    Elliptical3DTransform e3Dt;

    @Parameter
    double rMin=0.9, rMax= 1.1;

    @Override
    public void run() {

        RealRandomAccessible<UnsignedShortType> rra = (new Procedural3DImageShort(
            p -> {
              if ((p[0]>rMin)&&(p[0]<rMax)) {
                  return 255;
              } else {
                  return 0;
              }
            })).getRRA();


        Interval interval = new FinalInterval(
                new long[]{ -100, -100, -100 },
                new long[]{ 100, 100, 100 });

        final UnsignedShortType type = rra.realRandomAccess().get();

        final Source< UnsignedShortType > s = new RealRandomAccessibleIntervalSource<>( rra, interval, type, new AffineTransform3D(), "Ellipse" );

        WarpedSource ws = new WarpedSource(s,"Ellipsoid");
        ws.updateTransform(e3Dt.inverse());
        ws.setIsTransformed(true);

        sac_out = SourceAndConverterUtils.createSourceAndConverter(ws);

        e3Dt.updateNotifiers.add(() -> {
            ws.updateTransform(e3Dt.inverse());
            SourceAndConverterServices
                    .getSourceAndConverterDisplayService()
                    .getDisplaysOf(sac_out).forEach(bdvHandle -> bdvHandle.getViewerPanel().requestRepaint());
        });

    }
}
