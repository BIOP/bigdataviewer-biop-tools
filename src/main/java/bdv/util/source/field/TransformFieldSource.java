package bdv.util.source.field;

import bdv.viewer.Interpolation;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.position.FunctionRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;

public class TransformFieldSource implements ITransformFieldSource {

    final RealTransform transform;
    final int targetDimensions;
    final int sourceDimensions;
    final String name;

    public TransformFieldSource(RealTransform transform, String name) throws UnsupportedOperationException {
        this.transform = transform;
        this.sourceDimensions = transform.numSourceDimensions();
        this.targetDimensions = transform.numTargetDimensions();
        this.name = name;
    }

    @Override
    public boolean isPresent(int t) {
        return true; // always present
    }

    @Override
    public RandomAccessibleInterval<RealPoint> getSource(int t, int level) {
        throw new UnsupportedOperationException("The raster source of a transform field source is not defined");
    }

    @Override
    public RealRandomAccessible<RealPoint> getInterpolatedSource(int t, int level, Interpolation method) {
        RealTransform transformCopy = transform.copy();
        return new FunctionRealRandomAccessible<>(sourceDimensions, (position, value) -> {
            transformCopy.apply(position, value);
        }, this::getType); // new or keep ?
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        transform.identity();
    }

    @Override
    public RealPoint getType() {
        return new RealPoint(targetDimensions);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return new FinalVoxelDimensions("pixel",1,1,1);
    }

    @Override
    public int getNumMipmapLevels() {
        return 1; // I never remember if it's zero or 1
    }

    @Override
    public int numSourceDimensions() {
        return transform.numSourceDimensions();
    }

    @Override
    public int numTargetDimensions() {
        return transform.numTargetDimensions();
    }
}
