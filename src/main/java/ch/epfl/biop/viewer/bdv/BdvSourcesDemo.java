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
package ch.epfl.biop.viewer.bdv;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.service.SourceServices;

/**
 * Source Selector Behaviour Demo
 */

public class BdvSourcesDemo {

    static public void main(String... args) throws Exception {
        // Creates a demo bdv frame with demo images
        BdvHandle bdvh = initAndShowSources();

        RectangleSelectorBehaviour rsb = new RectangleSelectorBehaviour(bdvh, "Please select a rectangle");
        rsb.install();
        rsb.waitForSelection(10000);
        rsb.uninstall();
    }

    public static BdvHandle initAndShowSources() throws Exception {
        // load and convert the famous blobs image
        ImagePlus imp = IJ.openImage("src/test/resources/blobs.tif");
        RandomAccessibleInterval blob = ImageJFunctions.wrapReal(imp);

        // load 3d mri image spimdataset
        SpimData sd = new XmlIoSpimData().load("src/test/resources/mri-stack.xml");

        // Display mri image
        BdvStackSource bss = BdvFunctions.show(sd).get(0);
        bss.setDisplayRange(0,255);

        // Gets reference of BigDataViewer
        BdvHandle bdvh = bss.getBdvHandle();
        SourceServices.getBdvDisplayService().registerBdvHandle(bdvh);

        // Defines location of blobs image
        AffineTransform3D m = new AffineTransform3D();
        m.rotate(0,Math.PI/4);
        m.translate(256, 0,0);

        // Display first blobs image
        BdvFunctions.show(blob, "Blobs Rot X", BdvOptions.options().sourceTransform(m).addTo(bdvh));

        // Defines location of blobs image
        m.identity();
        m.rotate(2,Math.PI/4);
        m.translate(0,256,0);

        // Display second blobs image
        BdvFunctions.show(blob, "Blobs Rot Z ", BdvOptions.options().sourceTransform(m).addTo(bdvh));

        // Defines location of blobs image
        m.identity();
        m.rotate(2,Math.PI/6);
        m.rotate(0,Math.PI/720);
        m.translate(312,256,0);

        // Display third blobs image
        BdvFunctions.show(blob, "Blobs Rot Z Y ", BdvOptions.options().sourceTransform(m).addTo(bdvh));

        // Sets BigDataViewer view
        m.identity();
        m.scale(0.75);
        m.translate(150,100,0);

        bdvh.getViewerPanel().state().setViewerTransform(m);
        bdvh.getViewerPanel().requestRepaint();

        return bdvh;
    }

}
