package ch.epfl.biop.command.workflow.elliptic;

import bdv.img.WarpedSource;
import bdv.util.*;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.BoundingBoxEstimation;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import sc.fiji.bdvpg.scijava.services.SourceService;
import sc.fiji.bdvpg.services.SourceServices;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.display.BrightnessAdjuster;


//@Plugin(type = Command.class, menuPath = BdvPgMenus.RootMenu+"Source>Transform>Create Ellipsoid Source",
//        description = "Creates an ellipsoid source from an elliptical 3D transform for visualization")
public class DisplayEllipseFromTransformCommand implements Command {

    @Parameter(type = ItemIO.OUTPUT,
            label = "Ellipsoid Source",
            description = "The generated ellipsoid source for visualization")
    SourceAndConverter<?> source_out;

    @Parameter(type = ItemIO.BOTH,
            label = "Elliptical Transform",
            description = "The elliptical 3D transform defining the ellipsoid shape")
    Elliptical3DTransform e3dt;

    @Parameter(label = "Min Radius",
            description = "Inner radius threshold for the ellipsoid shell",
            style = "format:0.#####E0")
    double r_min =0.9;

    @Parameter(label = "Max Radius",
            description = "Outer radius threshold for the ellipsoid shell",
            style = "format:0.#####E0")
    double r_max = 1.1;

    @Parameter
    SourceService source_service;

    @Override
    public void run() {
        RealRandomAccessible<UnsignedShortType> rra = (new Procedural3DImageShort(
            p -> {
              if ((p[0]> r_min)&&(p[0]< r_max)) {
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
        ws.setBoundingBoxEstimator(new BoundingBoxEstimation(BoundingBoxEstimation.Method.CORNERS));
        ws.updateTransform(e3dt.inverse());
        ws.setIsTransformed(true);

        source_out = SourceHelper.createSourceAndConverter(ws);
        source_service.register(source_out);

        e3dt.updateNotifiers.add(() -> {
            ws.updateTransform(e3dt.inverse());
            SourceServices
                    .getBdvDisplayService()
                    .getDisplaysOf(source_out).forEach(bdvHandle -> bdvHandle.getViewerPanel().requestRepaint());
        });

        new BrightnessAdjuster(source_out,0,255).run();


    }
}
