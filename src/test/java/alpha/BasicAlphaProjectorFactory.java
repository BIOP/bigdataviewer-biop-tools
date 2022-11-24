package alpha;

import bdv.viewer.SourceAndConverter;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class BasicAlphaProjectorFactory implements AccumulateProjectorFactory<ARGBType> {

    public BasicAlphaProjectorFactory() {
        System.out.print(this.getClass()+"\t");
    }

    public VolatileProjector createProjector(
            final List< VolatileProjector > sourceProjectors,
            final List<SourceAndConverter< ? >> sources,
            final List<? extends RandomAccessible<? extends ARGBType>> sourceScreenImages,
            final RandomAccessibleInterval<ARGBType> targetScreenImage,
            final int numThreads,
            final ExecutorService executorService )
    {
        return new AccumulateProjectorARGBGeneric( sourceProjectors, sourceScreenImages, targetScreenImage, numThreads, executorService );
    }

    public static class AccumulateProjectorARGBGeneric extends AccumulateProjector< ARGBType, ARGBType >
    {
        public AccumulateProjectorARGBGeneric(
                final List< VolatileProjector > sourceProjectors,
                final List< ? extends RandomAccessible< ? extends ARGBType > > sources,
                final RandomAccessibleInterval< ARGBType > target,
                final int numThreads,
                final ExecutorService executorService )
        {
            super( sourceProjectors, sources, target );
        }

        @Override
        protected void accumulate(final Cursor< ? extends ARGBType >[] accesses, final ARGBType target )
        {
            int aSum = 0, rSum = 0, gSum = 0, bSum = 0;
            int length = accesses.length-1;
            for (int iSource = 0; iSource<length; iSource+=2) {
                final Cursor< ? extends ARGBType > access = accesses[iSource];
                final Cursor< ? extends ARGBType > access_alpha = accesses[iSource+1];
                final float alpha = Float.intBitsToFloat(access_alpha.get().get());
                final int value = access.get().get();
                final int a = (int) (ARGBType.alpha( value )*alpha);
                final int r = (int) (ARGBType.red( value )*alpha);
                final int g = (int) (ARGBType.red( value )*alpha);
                final int b = (int) (ARGBType.red( value )*alpha);
                aSum += a;
                rSum += r;
                gSum += g;
                bSum += b;
            }
            if ( aSum > 255 )
                aSum = 255;
            if ( rSum > 255 )
                rSum = 255;
            if ( gSum > 255 )
                gSum = 255;
            if ( bSum > 255 )
                bSum = 255;
            target.set( ARGBType.rgba( rSum, gSum, bSum, aSum ) );
        }
    }
}
