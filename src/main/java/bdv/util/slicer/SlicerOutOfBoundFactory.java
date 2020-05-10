package bdv.util.slicer;

import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.outofbounds.OutOfBounds;
import net.imglib2.outofbounds.OutOfBoundsFactory;

public class SlicerOutOfBoundFactory< T, F extends Interval & RandomAccessible< T >> implements OutOfBoundsFactory< T, F > {

    final int shiftedAxis;
    final int displayerAxis;

    public SlicerOutOfBoundFactory (int shiftedAxis, int displayerAxis) {
        this.displayerAxis = displayerAxis;
        this.shiftedAxis = shiftedAxis;
    }


    @Override
    public OutOfBounds<T> create(F f) {
        return new SlicerOutOfBounds<T>(f, shiftedAxis, displayerAxis);
    }
}
