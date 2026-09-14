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
package ch.epfl.biop.command.process.transform;

import bdv.img.WarpedSource;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ij.gui.WaitForUserDialog;
import net.imglib2.realtransform.RealTransform;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.viewer.bdv.BdvHandleHelper;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.register.BigWarpLauncher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static bdv.util.RealTransformHelper.BigWarpFileFromRealTransform;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = BdvPgMenus.RootMenu+"Register>Source - Edit Sources Warping",
        description = "Opens BigWarp to edit the warping transform of already-warped sources")
public class SourcesWarpingEditCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Moving Source(s)",
            type = ItemIO.BOTH,
            description = "The warped sources to edit (must be WarpedSource type)")
    SourceAndConverter<?>[] moving_sources;


    @Parameter(label = "Fixed Source(s)",
            required = false,
            description = "Optional reference sources for visual alignment")
    SourceAndConverter<?>[] fixed_sources;

    @Parameter(label = "2D Mode",
            description = "When checked, constrains editing to 2D")
    boolean is2d;

    Runnable waitForUser = () -> {
        WaitForUserDialog dialog = new WaitForUserDialog("Edit Registration","Please perform carefully your registration then press ok.");
        dialog.show();
    };

    @Override
    public void run() {
        for (SourceAndConverter<?> source : moving_sources) {
            if (!(source.getSpimSource() instanceof WarpedSource)) {
                System.err.println(source.getSpimSource().getName()+" is not a Warped source, it cannot be edited");
            }
        }

        RealTransform rt = ((WarpedSource) moving_sources[0].getSpimSource()).getTransform();

        List<SourceAndConverter<?>> movingSacs = Arrays.stream(moving_sources).collect(Collectors.toList());

        List<ConverterSetup> converterSetups = Arrays.stream(moving_sources).map(src -> SourceServices.getSourceService().getConverterSetup(src)).collect(Collectors.toList());
        List<SourceAndConverter<?>> fixedSacs;

        if (fixed_sources !=null) {
            fixedSacs = Arrays.stream(fixed_sources).collect(Collectors.toList());
            converterSetups.addAll(Arrays.stream(fixed_sources).map(src -> SourceServices.getSourceService().getConverterSetup(src)).collect(Collectors.toList()));
        } else {
            fixedSacs = new ArrayList<>();
        }

        // Launch BigWarp
        BigWarpLauncher bwl = new BigWarpLauncher(movingSacs, fixedSacs, "Big Warp", converterSetups);
        bwl.set2d();
        bwl.run();

        // Output bdvh handles -> will be put in the object service
        BdvHandle bdvhQ = bwl.getBdvHandleQ();
        BdvHandle bdvhP = bwl.getBdvHandleP();

        bdvhP.getViewerPanel().state().setViewerTransform(BdvHandleHelper.getViewerTransformWithNewCenter(bdvhP, new double[]{0,0,0}));
        bdvhQ.getViewerPanel().state().setViewerTransform(BdvHandleHelper.getViewerTransformWithNewCenter(bdvhQ, new double[]{0,0,0}));

        SourceServices.getBdvDisplayService().pairClosing(bdvhQ,bdvhP);

        bdvhP.getViewerPanel().requestRepaint();
        bdvhQ.getViewerPanel().requestRepaint();

        bwl.getBigWarp().getLandmarkFrame().repaint();

        bwl.getBigWarp().loadLandmarks(BigWarpFileFromRealTransform(rt));
        //bwl.getBigWarp().setInLandmarkMode(true);
        bwl.getBigWarp().setIsMovingDisplayTransformed(true);

        waitForUser.run();

        rt = bwl.getBigWarp().getBwTransform().getTransformation();

        bwl.getBigWarp().closeAll();

        for (SourceAndConverter<?> source : moving_sources) {
            WarpedSource src = ((WarpedSource) source.getSpimSource());
            src.updateTransform(rt);
            src.setIsTransformed(true);
            if (source.asVolatile() != null) {
                WarpedSource vsrc = (WarpedSource) source.asVolatile().getSpimSource();
                vsrc.updateTransform(rt);
                vsrc.setIsTransformed(true);
            }
        }

    }
}
