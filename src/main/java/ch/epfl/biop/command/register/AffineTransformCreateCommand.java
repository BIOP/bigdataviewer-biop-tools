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
package ch.epfl.biop.command.register;

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

/**
 * Command to create a 3D affine transform from a 4x4 matrix specification.
 */
@Plugin(type = BdvPlaygroundActionCommand.class, initializer = "init",
        menuPath = BdvPgMenus.RootMenu+"Process>Transform>New Affine Transform",
        description = "Creates an affine transform from a 4x4 matrix for use in other commands",
        headless = true)
public class AffineTransformCreateCommand implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(AffineTransformCreateCommand.class);

    @Parameter(label = "Transform Matrix",
            description = "A 4x3 affine transform matrix with 16 comma-separated values",
            style = "text area")
    String string_matrix = "1,0,0,0,\n 0,1,0,0,\n 0,0,1,0, \n 0,0,0,1";

    @Parameter(type = ItemIO.OUTPUT,
            label = "Affine Transform",
            description = "The created 3D affine transform")
    AffineTransform3D at3d;

    @Override
    public void run() {
        at3d = new AffineTransform3D();
        at3d.set(toDouble());
    }

    /**
     * Converts the input string matrix to a double array.
     * Handles both comma-separated matrix values and AffineTransform3D toString() format.
     *
     * @return array of 16 doubles representing the 4x4 transformation matrix
     */
    public double[] toDouble() {
        String inputString = string_matrix;
        // Test if the String is written using AffineTransform3D toString() method
        String[] testIfParenthesis = string_matrix.split("[\\(\\)]+");// right of left parenthesis

        for (String str : testIfParenthesis) {
            System.out.println(str);
        }

        if (testIfParenthesis.length > 1) {
            inputString = testIfParenthesis[1] + ",0,0,0,1";
        }

        String[] strNumber = inputString.split(",");
        double[] mat = new double[16];
        if (strNumber.length!=16) {
            logger.error("Matrix has not enough elements, 16 expected, "+strNumber.length+" provided.");
            return null;
        }
        for (int i=0;i<16;i++) {
            mat[i] = Double.valueOf(strNumber[i].trim());
        }
        return mat;
    }

}
