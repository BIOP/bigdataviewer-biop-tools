package ch.epfl.biop.bdv.commands;

import bdv.img.WarpedSource;
import bdv.util.*;
import bdv.viewer.Source;
import ch.epfl.biop.bdv.process.Procedural3DImageShort;
import ch.epfl.biop.bdv.transform.Elliptical3DTransform;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Transformation>Display Ellipsoid")
public class DisplayEllipseFromTransform implements Command {

    @Parameter(label = "Open in new BigDataViewer window")
    public boolean createNewWindow;

    // ItemIO.BOTH required because it can be modified in case of appending new data to BDV (-> requires INPUT), or created (-> requires OUTPUT)
    @Parameter(label = "BigDataViewer Frame", type = ItemIO.BOTH, required = false)
    public BdvHandle bdv_h;

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


        final AxisOrder axisOrder = AxisOrder.getAxisOrder( BdvOptions.options().values.axisOrder(), rra, false );
        final AffineTransform3D sourceTransform = BdvOptions.options().values.getSourceTransform();
        final UnsignedShortType type = rra.realRandomAccess().get();

        final Source< UnsignedShortType > s = new RealRandomAccessibleIntervalSource<>( rra, interval, type, sourceTransform, "Ellipse" );

        WarpedSource ws = new WarpedSource(s,"Ellipsoid");
        ws.updateTransform(e3Dt.inverse());
        ws.setIsTransformed(true);

        e3Dt.updateNotifiers.add(() -> {
            ws.updateTransform(e3Dt.inverse());
            this.bdv_h.getViewerPanel().requestRepaint();
        }); // TODO avoid memory leak somehow...

        BdvOptions options = BdvOptions.options();

        if (!createNewWindow) {
            options.addTo(bdv_h);
        }

        bdv_h = BdvFunctions.show( ws, options ).getBdvHandle();

    }
}
