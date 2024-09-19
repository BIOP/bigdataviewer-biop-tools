package ch.epfl.biop.scijava.command.spimdata;

import bdv.tools.boundingbox.BoxSelectionOptions;
import bdv.tools.boundingbox.TransformedRealBoxSelectionDialog;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceResampler;

import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDV>LLS7 - Crop 3D")
public class LLS7CropCommand implements Command {

    @Parameter
    BdvHandle bdvh;

    @Parameter
    String image_name;

    @Parameter
    SourceAndConverter<?>[] sources;

    //@Parameter
    String message = "Select the bounds of the 3D image";

    /*@Parameter(style = "format:0.#####E0")
    double xmin, xmax, ymin, ymax, zmin, zmax;

    @Parameter(style = "format:0.#####E0")
    double xmin_ini = 1.2, xmax_ini = 0.8,
           ymin_ini = -2.0*Math.PI, ymax_ini = 2.0*Math.PI,
           zmin_ini = -4.0*Math.PI, zmax_ini = 4.0*Math.PI;*/

    //@Parameter
    double box_size_x = 150/4.0;
    double box_size_y = 150/4.0;
    double box_size_z = 150/4.0;

    @Parameter(type = ItemIO.OUTPUT)
    RealInterval interval;

    @Parameter(type = ItemIO.OUTPUT)
    Boolean result;

    @Parameter
    SourceAndConverterService sac_service;

    @Parameter
    SourceAndConverterBdvDisplayService sac_display_service;

    public void run() {

        double[] center = BdvHandleHelper.getWindowCentreInCalibratedUnits(bdvh);

        final RealInterval initialInterval = Intervals.createMinMaxReal(
                center[0]-box_size_x, center[1]-box_size_y, center[2]-box_size_z,
                center[0]+box_size_x, center[1]+box_size_y, center[2]+box_size_z );
        final RealInterval rangeInterval = Intervals.createMinMaxReal(
                center[0]-5*box_size_x, center[1]-5*box_size_y, center[2]-5*box_size_z,
                center[0]+5*box_size_x, center[1]+5*box_size_y, center[2]+5*box_size_z
        //        0, 0, 0, box_size*5, box_size*5, box_size*5
        );

        final TransformedRealBoxSelectionDialog.Result result = BdvFunctions.selectRealBox(
                bdvh,
                new AffineTransform3D(),
                initialInterval,
                rangeInterval,
                BoxSelectionOptions.options()
                        .title( message ) );

        this.result = result.isValid();

        if (this.result)
        {
            interval = result.getInterval();
            // Assert : crop in micrometer, 0.144 micron per pixel

            SourceAndConverter<?> model = new EmptySourceAndConverterCreator(image_name, interval, 0.144,0.144,0.144).get();

            List<SourceAndConverter<?>> generatedSources = new ArrayList<>();

            for (int iCh = 0; iCh<sources.length; iCh++) {
                generatedSources.add(new SourceResampler<>((SourceAndConverter) sources[iCh], model, image_name + "_ch_"+iCh, false, true, false, 0).get());
            }

            BdvHandle bdvHandle = sac_display_service.getNewBdv();

            for (SourceAndConverter<?> source : generatedSources) {
                sac_service.register(source);
            }

            sac_display_service.show(bdvHandle, true, generatedSources.toArray(new SourceAndConverter[0]));

            new ViewerTransformAdjuster(bdvHandle, generatedSources.get(0)).run();

        }
    }

}
