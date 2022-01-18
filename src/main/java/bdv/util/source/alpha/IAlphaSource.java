package bdv.util.source.alpha;

import bdv.viewer.Source;
import net.imglib2.Interval;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.real.FloatType;

/**
 * Empty interface which serves as identifying alpha sources
 *
 * Any {@link bdv.viewer.Source} which needs to be identified as an alpha source should implement this interface
 * so far, this identification is only done in {@link sc.fiji.bdvpg.bdv.supplier.alpha.AlphaBdvSupplier}
 *
 * @author Nicolas Chiaruttini, 2021, EPFL
 */
public interface IAlphaSource extends Source<FloatType> {
    boolean intersectBox(AffineTransform3D affineTransform, Interval cell, int timepoint);
}
