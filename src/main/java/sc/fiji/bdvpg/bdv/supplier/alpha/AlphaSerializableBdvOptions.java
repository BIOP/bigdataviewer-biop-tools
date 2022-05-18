package sc.fiji.bdvpg.bdv.supplier.alpha;

import bdv.util.AxisOrder;
import bdv.util.BdvOptions;
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
    public long targetRenderNanos = 30 * 1000000l;

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
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public int numTimePoints = 1;

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    public AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory = new LayerAlphaProjectorFactory();

    /**
     * See do above. I'm writing this because CI does not pass if there are too many warnings
     */
    boolean useAlphaCompositing = true;

    // Not serialized
    //private TransformEventHandlerFactory transformEventHandlerFactory = TransformEventHandler3D::new;
    //private InputTriggerConfig inputTriggerConfig = null;
    //private final AffineTransform3D sourceTransform = new AffineTransform3D();

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
