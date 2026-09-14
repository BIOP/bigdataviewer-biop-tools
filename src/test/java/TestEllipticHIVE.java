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
import net.imagej.ImageJ;

public class TestEllipticHIVE {

    public static void main(String... args) {


        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        String macro = "" +
                "run(\"Open XML BDV Datasets\", \"\");\n" +
                // For real data HIVE
                // "run(\"New Elliptic 3D Transform\", \"r1=307.0 r2=866.0 r3=772.0 rx=1.0 ry=0.83 rz=1.77 tx=1475.0 ty=974.0 tz=434.0\");\n" +
                // For test resources data
                "run(\"New Elliptic 3D Transform\", \"r1=100.0 r2=100.0 r3=100.0 rx=0 ry=0 rz=0 tx=110.0 ty=110.0 tz=200.0\");\n" +
                "run(\"Elliptic 3D Transform Sources\", \"\");\n" +
                "run(\"Export elliptic 3D transformed sources (interactive box)\", \"\");\n" +
                //"run(\"Export elliptic 3D transformed sources\", \"\");\n" +
                "//run(\"Brightness/Contrast...\");\n";

        ij.script().run("dummy.ijm", macro, true);

        //ImagePlusHelper.wrap()
    }
}
