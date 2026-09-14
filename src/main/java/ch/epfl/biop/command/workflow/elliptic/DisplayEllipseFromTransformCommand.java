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
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.display.BrightnessAdjuster;


//@Plugin(type = Command.class, menuPath = BdvPgMenus.RootMenu+"Source>Transform>Create Ellipsoid Source",
//        description = "Creates an ellipsoid source from an elliptical 3D transform for visualization")
public class DisplayEllipseFromTransformCommand implements BdvPlaygroundActionCommand {

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
