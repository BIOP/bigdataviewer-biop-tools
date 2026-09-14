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
import ij.IJ;
import ij.ImagePlus;
import loci.common.DebugTools;
import net.imagej.ImageJ;

import java.io.IOException;


public class TestTimeShiftedSources {





    static public void main(String... args) throws IOException {
        final ImageJ ij = new ImageJ();

        DebugTools.enableLogging("INFO");
        ij.ui().showUI();

        //dowloadBrainVSIDataset();
        ImagePlus imp = IJ.openImage("http://imagej.net/images/blobs.gif");
        imp.show();
        IJ.run(imp, "Invert LUT", "");
        IJ.run(imp, "Make BDVDataset from current IJ1 image", "");
        IJ.run("Create a time-shifted source", "source=blobs.gif timeshift=-3 name=shift-3");
        IJ.run("Create a time-shifted source", "source=blobs.gif timeshift=+3 name=shift+3");
        IJ.run(imp, "Display Sources On Grid", "timepoint_begin=0 n_columns=6 entities_split=imagename");

        imp.show();

    }




}
