package ch.epfl.biop.command.display.bdv.settings;


import org.scijava.Context;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import sc.fiji.bdvpg.viewers.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.services.SourceBdvDisplayService;

import java.util.Arrays;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.DisplayMenu, weight = BdvPgMenus.DisplayW),
                @Menu(label = BdvPgMenus.BDVMenu, weight = BdvPgMenus.BDVW),
                @Menu(label = "Settings", weight = -2),
                @Menu(label = "BDV - Set Style (Alpha)", weight = -1.5)
        },
        description = "Set preferences of Bdv Window (Alpha)")
public class BdvStyleAlphaSetCommand implements BdvPlaygroundActionCommand{

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
    boolean white_background = false;

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
    SourceBdvDisplayService sourceDisplayService;

    @Override
    public void run() {
        if (resetToDefault) {
            IBdvSupplier bdvSupplier = new AlphaBdvSupplier(new AlphaSerializableBdvOptions());
            sourceDisplayService.setDefaultBdvSupplier(bdvSupplier);
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
            options.white_bg = white_background;
            IBdvSupplier bdvSupplier = new AlphaBdvSupplier(options);
            sourceDisplayService.setDefaultBdvSupplier(bdvSupplier);
        }

    }
}
