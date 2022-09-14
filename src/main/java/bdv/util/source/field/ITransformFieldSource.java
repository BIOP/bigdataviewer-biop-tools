package bdv.util.source.field;

import bdv.viewer.Source;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.RealTransform;

public interface ITransformFieldSource extends Source<RealPoint> {

    int numSourceDimensions();

    int numTargetDimensions();

    RealTransform getTransform();
}
