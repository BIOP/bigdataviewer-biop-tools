/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package ch.epfl.biop.command.display.bdv.settings;

import org.scijava.Context;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.alpha.AlphaSerializableBdvOptions;
import sc.fiji.bdvpg.viewer.bdv.supplier.IBdvSupplier;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;

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
