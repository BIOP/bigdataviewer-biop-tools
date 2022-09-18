package bdv.util.source.field;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;

public class ExtendedRealPoint extends RealPoint {
    public ExtendedRealPoint(int nDimensions) {
        super(nDimensions);
    }

    public ExtendedRealPoint( final RealLocalizable localizable ) {
        super(localizable);
    }

    public void mul(double alpha) {
        for (int d = 0; d<this.n; d++) {
            position[d] = alpha * position[d];
        }
    }
}
