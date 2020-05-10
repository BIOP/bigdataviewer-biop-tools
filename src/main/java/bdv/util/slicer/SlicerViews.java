package bdv.util.slicer;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.ExtendedRandomAccessibleInterval;

public class SlicerViews {

    public static < T, F extends
            RandomAccessibleInterval< T > > ExtendedRandomAccessibleInterval< T, F > extendSlicer( final F source, int shiftedAxis, int displayerAxis )
    {
        return new ExtendedRandomAccessibleInterval<>( source, new SlicerOutOfBoundFactory<>( shiftedAxis, displayerAxis) );
    }
}
