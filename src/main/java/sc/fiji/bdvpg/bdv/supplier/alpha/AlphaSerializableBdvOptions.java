package sc.fiji.bdvpg.bdv.supplier.alpha;

import bdv.util.AxisOrder;
import bdv.util.BdvOptions;
import bdv.util.projector.alpha.LayerAlphaProjectorFactory;
import bdv.viewer.render.AccumulateProjectorFactory;
import net.imglib2.type.numeric.ARGBType;

public class AlphaSerializableBdvOptions {

    public int width = -1;

    public int height = -1;

    //public double[] screenScales = new double[] {  0.25, 0.125, 0.125/4.0 }; // 1, 0.75, 0.5,

    public double[] screenScales = new double[] { 1, 0.5, 0.25, 0.125 };

    public long targetRenderNanos = 30 * 1000000l;

    public int numRenderingThreads = 5;

    public int numSourceGroups = 10;

    public String frameTitle = "BigDataViewer";

    public boolean is2D = false;

    public AxisOrder axisOrder = AxisOrder.DEFAULT;

    // Extra for the playground
    public boolean interpolate = false;

    public int numTimePoints = 1;

    public AccumulateProjectorFactory<ARGBType> accumulateProjectorFactory = new LayerAlphaProjectorFactory();

    boolean useAlphaCompositing = true;

    // Not serialized
    //private TransformEventHandlerFactory transformEventHandlerFactory = TransformEventHandler3D::new;
    //private InputTriggerConfig inputTriggerConfig = null;
    //private final AffineTransform3D sourceTransform = new AffineTransform3D();

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
