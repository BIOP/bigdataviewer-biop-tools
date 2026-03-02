package ch.epfl.biop.command.workflow.elliptic;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.command.view.region.GetUserBoxCommand;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceBdvDisplayService;
import sc.fiji.bdvpg.source.SourceHelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Command used to export an elliptical transformed source
 */

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = BdvPgMenus.RootMenu+"Specialized Workflows>Elliptic Transform>Source - Export Elliptic 3D Transformed Sources (Interactive Box)",
        description = "Interactively select a region in spherical coordinates and export to ImagePlus")
public class EasyExportEllipticProjection implements Command {

    @Parameter(label = "Select Source(s)",
            callback = "validateMessage",
            description = "Elliptically-transformed sources to export")
    SourceAndConverter<?>[] sources;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The exported image")
    public ImagePlus imp_out;

    @Parameter
    CommandService cs;

    @Parameter
    SourceBdvDisplayService displayService;

    @Override
    public void run() {
        try {

            List<SourceAndConverter<?>> sources = sorter.apply(Arrays.asList(this.sources));

            BdvHandle bdvh = displayService.getNewBdv();
            displayService.show(bdvh, sources.toArray(new SourceAndConverter[0]));

            AffineTransform3D view = new AffineTransform3D();

            view.set(0,0,30,415,0,30,0,260,-30,0,0,30);

            // [INFO] BigDataViewer: Viewer Transform: 3d-affine: (0.0, 0, 30.912680532870755, 414.9153888353502,
            // 0, 30.912680532870755, 0, 263.410858153052,
            // -30.912680532870777, 0, 0, 31.250781886278737)
            //view.scale(1.0/bdvh.getViewerPanel().getWidth());
            //view.rotate(1, Math.PI/2.0);

            bdvh.getViewerPanel().state().setViewerTransform(view);

            // At least one source
            if ((this.sources ==null)||(this.sources.length==0)) {
                IJ.log("No selected source. Abort command.");
                return;
            }

            CommandModule boxSelector =
                    cs.run(GetUserBoxCommand.class, true,
                            "bdvh", bdvh,
                                    "message", "Select (r, theta, phi) bounds to crop",
                                    "zmin", -4.0*Math.PI,
                                    "zmax",  4.0*Math.PI,
                                    "ymin", -2.0*Math.PI,
                                    "ymax",  2.0*Math.PI,
                                    "xmin", 0,
                                    "xmax", 3,
                                    "zmin_ini", -2.0*Math.PI,
                                    "zmax_ini",  2.0*Math.PI,
                                    "ymin_ini", -Math.PI,
                                    "ymax_ini",  Math.PI,
                                    "xmin_ini", 0.7,
                                    "xmax_ini", 1.3
                            ).get();


            if ((Boolean) boxSelector.getOutput("result")) {
                RealInterval interval = (RealInterval) boxSelector.getOutput("interval");
                Future<CommandModule> imageGetter =
                        cs.run(ExportEllipticProjection.class, true,
                                "sources", this.sources,
                                "r_min", interval.realMin(0),
                                "r_max", interval.realMax(0),
                                "theta_min", interval.realMin(1)*180.0/Math.PI,
                                "theta_max", interval.realMax(1)*180.0/Math.PI,
                                "phi_min", interval.realMin(2)*180.0/Math.PI,
                                "phi_max", interval.realMax(2)*180.0/Math.PI);

                imp_out = (ImagePlus) imageGetter.get().getOutput("imp_out");
            } else {
                IJ.log("Invalid box");
            }

            displayService.closeBdv(bdvh);
            bdvh.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Function<Collection<SourceAndConverter<?>>, List<SourceAndConverter<?>>> sorter = SourceHelper::sortDefaultGeneric;

}
