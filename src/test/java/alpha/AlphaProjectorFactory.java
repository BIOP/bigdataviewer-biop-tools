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

public class AlphaProjectorFactory implements AccumulateProjectorFactory<ARGBType> {

    final SourcesMetadata meta;

    public AlphaProjectorFactory(SourcesMetadata meta) {
        System.out.print(this.getClass()+"\t");
        this.meta = meta;
    }

    public VolatileProjector createProjector(
            final List< VolatileProjector > sourceProjectors,
            final List<SourceAndConverter< ? >> sources,
            final List<? extends RandomAccessible<? extends ARGBType>> sourceScreenImages,
            final RandomAccessibleInterval<ARGBType> targetScreenImage,
            final int numThreads,
            final ExecutorService executorService )
    {
        return new AccumulateProjectorARGBGeneric( meta, sourceProjectors, sources, sourceScreenImages, targetScreenImage, numThreads, executorService );
    }

    public static class AccumulateProjectorARGBGeneric extends AccumulateProjector< ARGBType, ARGBType >
    {

        final boolean[] is_alpha; // flags if the source in an alpha source ( not displayed )
        final boolean[] has_alpha; // flag if the source has an alpha channel, present in the list of sources
        final int[] sources_alpha_index; // index of the alpha channel, if any ( has_alpha is false otherwise )

        public AccumulateProjectorARGBGeneric(
                SourcesMetadata meta,
                final List< VolatileProjector > sourceProjectors,
                final List<SourceAndConverter< ? >> sources,
                final List< ? extends RandomAccessible< ? extends ARGBType > > sourceScreenImages,
                final RandomAccessibleInterval< ARGBType > target,
                final int numThreads,
                final ExecutorService executorService
                )
        {
            super( sourceProjectors, sourceScreenImages, target, numThreads, executorService );
            sources_alpha_index = new int[sources.size()];
            is_alpha = new boolean[sources.size()];
            has_alpha = new boolean[sources.size()];

            // Let's sort which sources are alpha, which are not, and which contain alpha channels, actually present in the projector
            for (int index_source=0; index_source<sources.size(); index_source++) {
                SourceAndConverter source = sources.get(index_source);
                int index_alpha_source;
                if (meta.isAlphaSource(source)) {
                    is_alpha[index_source] = true;
                } else {
                    // It's not an alpha source
                    if (meta.hasAlphaSource(source)) {
                        // It has an alpha, source, but is it in the list of sources ?
                        index_alpha_source = sources.indexOf(meta.getAlphaSource(source)); // returns -1 if the source does not exist
                        if (index_alpha_source!=-1) {
                            has_alpha[index_source] = true;
                            sources_alpha_index[index_source] = index_alpha_source;
                        } else {
                            has_alpha[index_source] = false;
                        }
                    } else {
                        has_alpha[index_source] = false;
                    }
                }
            }
        }

        @Override
        protected void accumulate(final Cursor< ? extends ARGBType >[] accesses, final ARGBType target )
        {
            int aSum = 0, rSum = 0, gSum = 0, bSum = 0;

            for (int iSource = 0; iSource<accesses.length;iSource++) {
                if (!is_alpha[iSource]) {
                    // Has an alpha channel : uses the alpha channel for the projection
                    if (has_alpha[iSource]) {
                        final Cursor< ? extends ARGBType > access = accesses[iSource];
                        final Cursor< ? extends ARGBType > access_alpha = accesses[sources_alpha_index[iSource]];
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
                    } else {
                        // No alpha channel: standard sum
                        final int value = accesses[iSource].get().get();
                        final int a = ARGBType.alpha( value );
                        final int r = ARGBType.red( value );
                        final int g = ARGBType.green( value );
                        final int b = ARGBType.blue( value );
                        aSum += a;
                        rSum += r;
                        gSum += g;
                        bSum += b;
                    }
                }
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

    public interface SourcesMetadata {
        boolean isAlphaSource(SourceAndConverter sac);
        boolean hasAlphaSource(SourceAndConverter sac);
        SourceAndConverter getAlphaSource(SourceAndConverter sac);
    }

}
