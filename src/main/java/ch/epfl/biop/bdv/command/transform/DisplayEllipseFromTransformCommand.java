package ch.epfl.biop.bdv.command.transform;

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
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.display.BrightnessAdjuster;


//@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Transform>Create Ellipsoid Source")
public class DisplayEllipseFromTransformCommand implements Command {

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter sac_out;

    @Parameter(type = ItemIO.BOTH)
    Elliptical3DTransform e3Dt;

    @Parameter(style = "format:0.#####E0")
    double rMin=0.9, rMax= 1.1;

    @Parameter
    SourceAndConverterService sacService;

    @Override
    public void run() {

        RealRandomAccessible<UnsignedShortType> rra = (new Procedural3DImageShort(
            p -> {
              if ((p[0]>rMin)&&(p[0]<rMax)) {
                  if ((p[1] > Math.PI/2.0)) { // poles are highlighted
                      return (int)(255.0*(1+0.25*Math.cos(20*p[2]))/2.0);
                  } else
                      return (int)(126.0*(1+0.25*Math.cos(20*p[2]))/2.0);
              } else {
                  return 0;
              }
            })).getRRA();


        Interval interval = new FinalInterval(
                new long[]{ -2, -2, -2 },
                new long[]{ 2, 2, 2 });

        final UnsignedShortType type = rra.realRandomAccess().get();

        final Source< UnsignedShortType > s = new RealRandomAccessibleIntervalSource<>( rra, interval, type, new AffineTransform3D(), "Ellipse" );

        WarpedSource ws = new WarpedSource(s,"Ellipsoid");
        ws.updateTransform(e3Dt.inverse());
        ws.setIsTransformed(true);

        sac_out = SourceAndConverterHelper.createSourceAndConverter(ws);

        e3Dt.updateNotifiers.add(() -> {
            ws.updateTransform(e3Dt.inverse());
            SourceAndConverterServices
                    .getBdvDisplayService()
                    .getDisplaysOf(sac_out).forEach(bdvHandle -> bdvHandle.getViewerPanel().requestRepaint());
        });

        new BrightnessAdjuster(sac_out,0,255).run();

        sacService.register(sac_out);

    }
}
