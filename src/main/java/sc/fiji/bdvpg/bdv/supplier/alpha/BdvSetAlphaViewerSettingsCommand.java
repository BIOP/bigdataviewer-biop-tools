package sc.fiji.bdvpg.bdv.supplier.alpha;


import org.scijava.Context;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.supplier.DefaultBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.SerializableBdvOptions;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;

import java.util.Arrays;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"BDV>BDV - Set BDV window (alpha)",
        description = "Set preferences of Bdv Window (Alpha)")
public class BdvSetAlphaViewerSettingsCommand implements BdvPlaygroundActionCommand{

    @Parameter(label = "Click this checkbox to ignore all parameters and reset the default alpha viewer", persist = false)
    boolean resetToDefault = false;

    @Parameter
    int width = 640;

    @Parameter
    int height = 480;

    @Parameter
    String screenscales = "1, 0.5, 0.25, 0.125";

    @Parameter
    long targetrenderms = 30;// * 1000000l;

    @Parameter
    int numrenderingthreads = 3;

    @Parameter
    int numsourcegroups = 10;

    @Parameter
    String frametitle = "BigDataViewer";

    @Parameter
    boolean is2d = false;

    @Parameter
    boolean interpolate = false;

    @Parameter
    int numtimepoints = 1;

    @Parameter
    boolean usealphalayer = true;

    //@Parameter
    //AxisOrder axisOrder = AxisOrder.DEFAULT;

    //AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory = new DefaultAccumulatorFactory();

    @Parameter
    Context ctx;

    @Parameter
    SourceAndConverterBdvDisplayService sacDisplayService;

    @Override
    public void run() {
        if (resetToDefault) {
            IBdvSupplier bdvSupplier = new AlphaBdvSupplier(new AlphaSerializableBdvOptions());
            sacDisplayService.setDefaultBdvSupplier(bdvSupplier);
        } else {
            AlphaSerializableBdvOptions options = new AlphaSerializableBdvOptions();
            options.frameTitle = frametitle;
            options.is2D = is2d;
            options.numRenderingThreads = numrenderingthreads;
            options.screenScales = Arrays.stream(screenscales.split(",")).mapToDouble(Double::parseDouble).toArray();
            options.height = height;
            options.width = width;
            options.numSourceGroups = numsourcegroups;
            options.numTimePoints = numtimepoints;
            options.interpolate = interpolate;
            options.useAlphaCompositing = usealphalayer;
            IBdvSupplier bdvSupplier = new AlphaBdvSupplier(options);
            sacDisplayService.setDefaultBdvSupplier(bdvSupplier);
        }

    }
}
