package bdv.util;

import bdv.util.slicer.SlicerViews;
import bdv.viewer.Source;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;

import java.util.ArrayList;
import java.util.List;

public class SlicedSource< T extends NumericType<T> & NativeType<T>> extends ResampledSource<T> {

    public SlicedSource(Source source, Source resamplingModel, boolean reuseMipMaps, boolean cache, boolean originInterpolation) {
        super(source, resamplingModel, reuseMipMaps, cache, originInterpolation);
    }

    @Override
    public RandomAccessibleInterval<T> buildSource(int t, int level) {
        RandomAccessibleInterval<T> nonResliced = super.buildSource(t,level);
        int nSlices = (int) nonResliced.dimension(2);
        List<RandomAccessibleInterval<T>> zSlices = new ArrayList<>(nSlices);

        return Views.interval(SlicerViews.extendSlicer(nonResliced), new FinalInterval(nonResliced.dimension(0), nonResliced.dimension(1)*60, 1));
        //return Views.addDimension(Views.concatenate(1,zSlices),0,0);
    }

}
