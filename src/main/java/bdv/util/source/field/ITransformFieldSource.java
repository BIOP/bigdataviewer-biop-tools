package bdv.util.source.field;

import bdv.viewer.Source;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.RealTransform;

public interface ITransformFieldSource<T extends RealLocalizable> extends Source<T> {

    int numSourceDimensions();

    int numTargetDimensions();

    RealTransform getTransform();
}
