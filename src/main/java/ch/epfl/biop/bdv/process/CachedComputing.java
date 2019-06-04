package ch.epfl.biop.bdv.process;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.img.Img;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;

import static java.lang.Math.abs;
import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

public class CachedComputing {

    /**
     * Finds the border of a label image (in fact, of any image)
     * Cached computing -> can be sciajava_volatile
     * @param lblImg
     * @param <T>
     * @return
     */

    static public <T extends Type<T> & Comparable<T> > Img<UnsignedShortType> get3DBorderLabelImage(RandomAccessibleInterval<T> lblImg) {
        // Make edge display on demand
        final int[] cellDimensions = new int[] { 32, 32, 32 };

        // Cached Image Factory Options
        final DiskCachedCellImgOptions factoryOptions = options()
                .cellDimensions( cellDimensions )
                .cacheType( DiskCachedCellImgOptions.CacheType.BOUNDED )
                .maxCacheSize( 100 );

        // Expand label image by one pixel to avoid out of bounds exception
        final RandomAccessibleInterval<T> lblImgWithBorder =  Views.expandBorder(lblImg,new long[] {1,1,1});

        // Creates cached image factory of Type Byte
        final DiskCachedCellImgFactory<UnsignedShortType> factory = new DiskCachedCellImgFactory<UnsignedShortType>( new UnsignedShortType(), factoryOptions );

        // Creates shifted views by one pixel in each dimension
        RandomAccessibleInterval<T> lblImgXShift = Views.translate(lblImgWithBorder,new long[]{1,0,0});
        RandomAccessibleInterval<T> lblImgYShift = Views.translate(lblImgWithBorder,new long[]{0,1,0});
        RandomAccessibleInterval<T> lblImgZShift = Views.translate(lblImgWithBorder,new long[]{0,0,1});

        // Creates border image, with cell Consumer method, which creates the image
        final Img<UnsignedShortType> borderLabel = factory.create( lblImg, cell -> {

            // Cursor on the source image
            final Cursor<T> in = Views.flatIterable( Views.interval( lblImg, cell ) ).cursor();

            // Cursor on shifted source image
            final Cursor<T> inXS = Views.flatIterable( Views.interval( lblImgXShift, cell ) ).cursor();
            final Cursor<T> inYS = Views.flatIterable( Views.interval( lblImgYShift, cell ) ).cursor();
            final Cursor<T> inZS = Views.flatIterable( Views.interval( lblImgZShift, cell ) ).cursor();

            // Cursor on output image
            final Cursor<UnsignedShortType> out = Views.flatIterable( cell ).cursor();

            // Loops through voxels
            while ( out.hasNext() ) {
                // Current value
                T v = in.next();
                // Equals in all shifted images ?
                int res = abs(v.compareTo(inXS.next()))
                        + abs(v.compareTo(inYS.next()))
                        + abs(v.compareTo(inZS.next()));

                out.next().set( (int) ( res>0?126:0 ) );
            }

        }, options().initializeCellsAsDirty( true ) );

        return borderLabel;
    }
}
