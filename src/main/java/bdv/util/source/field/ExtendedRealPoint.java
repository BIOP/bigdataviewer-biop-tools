package bdv.util.source.field;

import net.imglib2.RealPoint;

public class ExtendedRealPoint extends RealPoint {
    public ExtendedRealPoint(int nDimensions) {
        super(nDimensions);
    }

    public void mul(double alpha) {
        for (int d = 0; d<this.n; d++) {
            position[d] = alpha * position[d];
        }
    }
}
