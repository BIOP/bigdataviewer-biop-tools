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
package sc.fiji.bdvpg.bdv.supplier.alpha;

import bdv.util.AxisOrder;
import bdv.util.BdvOptions;
import bdv.util.projector.alpha.ILayerAlphaProjectorFactory;
import bdv.util.projector.alpha.LayerAlphaProjectorFactory;
import bdv.viewer.render.AccumulateProjectorFactory;
import net.imglib2.type.numeric.ARGBType;

public class AlphaSerializableBdvOptions {

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public int width = -1;

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public int height = -1;

    //public double[] screenScales = new double[] {  0.25, 0.125, 0.125/4.0 }; // 1, 0.75, 0.5,

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public double[] screenScales = new double[] { 1, 0.5, 0.25, 0.125 };

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public long targetRenderNanos = 30 * 1000000L;

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public int numRenderingThreads = 5;

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public int numSourceGroups = 10;

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public String frameTitle = "BigDataViewer";

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public boolean is2D = false;

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public AxisOrder axisOrder = AxisOrder.DEFAULT;

    /**
     * Extra arg for the playground
     */
    public boolean interpolate = false;

    /**
     * Extra arg for the playground
     */
    public boolean white_bg = false;

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public int numTimePoints = 1;

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public ILayerAlphaProjectorFactory accumulateProjectorFactory;// = new LayerAlphaProjectorFactory();

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public boolean useAlphaCompositing = true;

    public int fontSize = 18;

    public String font = "Courier";

    public boolean showSourcesNames = true;

    public boolean showCenterCross = true;

    public boolean showRayCastSlider = true;

    public boolean showSourceNavigatorSlider = true;

    public int numGroups = 10;

    public boolean showEditorCard = true;

    /**
     *
     * @return serializable bdv options
     */
    public BdvOptions getBdvOptions() {
        BdvOptions o =
                BdvOptions.options()
                        .screenScales(this.screenScales)
                        .targetRenderNanos(this.targetRenderNanos)
                        .numRenderingThreads(this.numRenderingThreads)
                        .numSourceGroups(this.numSourceGroups)
                        .axisOrder(this.axisOrder)
                        .preferredSize(this.width, this.height)
                        .frameTitle(this.frameTitle);
        if (this.accumulateProjectorFactory!=null) {
            o = o.accumulateProjectorFactory(this.accumulateProjectorFactory);
        }
        if (this.is2D) o = o.is2D();

        return o;
    }

}
