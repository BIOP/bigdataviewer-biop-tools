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
import bdv.util.*;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.source.SourceHelper;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.service.SourceServices;
import sc.fiji.bdvpg.dataset.importer.XMLToDatasetImporter;


public class Resample3DAlongAxis {

    static {
        LegacyInjector.preinit();
    }

    public static void main(String... args) {
        // Initializes static SourceService and Display Service

        ImageJ ij = new ImageJ();
        ij.ui().showUI();

        final String filePath = "src/test/resources/mri-stack.xml";
        // Import SpimData
        XMLToDatasetImporter importer = new XMLToDatasetImporter(filePath);
        //importer.run();

        final AbstractSpimData spimData = importer.get();

        SourceAndConverter source = SourceServices
                .getSourceService()
                .getSourcesFromDataset(spimData)
                .get(0);

        // Creates a BdvHandle
        //BdvHandle bdvHandle = SourceServices
        //        .getSourceAndConverterDisplayService().getActiveBdv();
        /*SourceServices
                .getSourceAndConverterDisplayService()
                .show(source);*/

        AffineTransform3D m = new AffineTransform3D();

        source.getSpimSource().getSourceTransform(0,0,m);

        RandomAccessibleIntervalSource<UnsignedShortType> rais;

        // DO NOT WORK
         rais = new RandomAccessibleIntervalSource<UnsignedShortType>(
                Views.expandZero(source.getSpimSource().getSource(0,0),0,0,0), // even though we don't care about the size of the border, this helps set the dimension
                new UnsignedShortType(),
                m,
                "RAIS"
        );

        RealPoint pt1 = new RealPoint(3);
        pt1.setPosition(new double[]{100,100,100});

        RealPoint pt2 = new RealPoint(3);
        pt2.setPosition(new double[]{130,150,150});

        Source alignedAlongZ = SourceHelper.AlignAxisResample(rais, pt1, pt2, 0.5, 300, 300, 20, true, true);

        RandomAccessibleInterval rai = alignedAlongZ.getSource(0,0);

        rai = Views.rotate(rai,0,1);

        ImageJFunctions.show(rai);
    }


}
