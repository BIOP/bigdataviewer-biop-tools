package bdv.util.source.field;

import bdv.viewer.Source;
import net.imglib2.RealPoint;

public interface ITransformFieldSource extends Source<RealPoint> {

    int numSourceDimensions();

    int numTargetDimensions();
}
