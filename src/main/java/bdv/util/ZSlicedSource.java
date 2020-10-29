package bdv.util;

import bdv.util.slicer.SlicerViews;
import bdv.viewer.Source;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;

import java.util.function.Supplier;

public class ZSlicedSource< T extends NumericType<T> & NativeType<T>> extends ResampledSource<T> {

    Supplier<Long> subSampler = () -> (long) 1;

    public ZSlicedSource(Source source, Source resamplingModel, boolean reuseMipMaps, boolean cache, boolean originInterpolation, Supplier<Long> subSampler) {
        super(source, resamplingModel, reuseMipMaps, cache, originInterpolation);
        this.subSampler = subSampler;
    }

    public ZSlicedSource(Source source, Source resamplingModel, boolean reuseMipMaps, boolean cache, boolean originInterpolation) {
        super(source, resamplingModel, reuseMipMaps, cache, originInterpolation);
    }

    @Override
    public RandomAccessibleInterval<T> buildSource(int t, int level) {
        RandomAccessibleInterval<T> nonResliced = super.buildSource(t,level);
        nonResliced = Views.subsample(nonResliced,1,1,subSampler.get());
        return Views.interval(SlicerViews.extendSlicer(nonResliced,2,0), new FinalInterval(nonResliced.dimension(0)*nonResliced.dimension(2), nonResliced.dimension(1), nonResliced.dimension(2)));
    }

    /** Better behaviour to just keep the scale and avoid reorienting
     *
     * @param t
     * @param level
     * @param transform
     */
    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        resamplingModel.getSourceTransform(t,reuseMipMaps?level:0,transform);
        transform.set(0,0,3);
        transform.set(0,1,3);
        transform.set(0,2,3);
        double xScale = getNormTransform(0, transform);
        double yScale = getNormTransform(1, transform);
        double zScale = getNormTransform(2, transform);
        transform.identity();
        transform.scale(xScale, yScale, zScale);
    }

    /**
     * Returns the norm of an axis after an affinetransform is applied
     * @param axis
     * @param t
     * @return
     */
    static public double getNormTransform(int axis, AffineTransform3D t) {
        double f0 = t.get(0,axis);
        double f1 = t.get(1,axis);
        double f2 = t.get(2,axis);
        return Math.sqrt(f0 * f0 + f1 * f1 + f2 * f2);
    }

}
