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
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.source.affine.AffineEditor;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the {@link AffineEditor} on four pairs of letters F, each moving letter rotated and shifted differently,
 * and prints the results.
 */
public class AffineEditorPairsDemo {

    public static void main(String... args) {
        new ImageJ();
        SourceAndConverter<?>[] fixed = {AffineEditorDemo.letterF("fixed", new AffineTransform3D(), new ARGBType(ARGBType.rgba(0, 255, 0, 255)))};
        List<AffineEditor.Pair> pairs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            AffineTransform3D offset = new AffineTransform3D();
            offset.rotate(2, 0.1 * (i + 1));
            offset.translate(10 * i, -10 * i, 0);
            SourceAndConverter<?>[] moving = {AffineEditorDemo.letterF("moving " + i, offset, new ARGBType(ARGBType.rgba(255, 0, 255, 255)))};
            pairs.add(new AffineEditor.Pair(fixed, moving, new AffineTransform3D(), new double[]{0, 0, 200, 200}, "letter " + i));
        }
        List<AffineTransform3D> result = AffineEditor.edit(pairs, 0, "Affine editor pairs demo");
        System.out.println(result == null ? "Cancelled" : "Applied: " + result);
        System.exit(0);
    }

}
