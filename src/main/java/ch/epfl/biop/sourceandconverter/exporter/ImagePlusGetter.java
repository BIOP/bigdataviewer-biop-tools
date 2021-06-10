package ch.epfl.biop.sourceandconverter.exporter;

import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import ij.ImageStack;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imagej.ImageJ;
import net.imagej.patcher.LegacyInjector;
import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.AbstractIntegerType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.spimdata.importer.SpimDataFromXmlImporter;

import java.nio.ByteBuffer;

public class ImagePlusGetter {

    public static ImagePlus getImagePlus(String name, RandomAccessibleInterval rai) {

        return new ImagePlus(name, getImageStack(rai));

    }

    public static ImageStack getImageStack(RandomAccessibleInterval rai) {
        assert rai.numDimensions() == 3;
        final Object type = Util.getTypeFromInterval(rai);
        final int sx = (int)rai.dimension(0);
        final int sy = (int)rai.dimension(1);
        final int sz = (int)rai.dimension(2);
        final int nPixPerPlane = sx*sy;
        System.out.println("nPixPerPlane = "+nPixPerPlane);
        ImageStack stack = new ImageStack(sx,sy);

        if (type instanceof UnsignedShortType) {
            stack.setBitDepth(16);
            short[] shorts = new short[nPixPerPlane];
            if (rai instanceof IterableInterval) {
                IterableInterval<UnsignedShortType> ii = (IterableInterval<UnsignedShortType>) rai;
                int idx = 0;
                //int idxZ = 0;
                for (Cursor<UnsignedShortType> s = ii.cursor(); s.hasNext();idx++) {
                    //int currentPix = s.next().get();
                    shorts[idx] = (short) s.next().get();//currentPix ;
                    //bytes[idx+1] = (byte) (currentPix >>> 8);
                    //System.out.println(idx);
                    if (idx == nPixPerPlane-1) {
                        idx = -1;
                        //idxZ++;
                        stack.addSlice("", shorts);
                        shorts = new short[nPixPerPlane];
                        /*if (idxZ != sz) {
                            bytes = new byte[nBytesPerPlane];
                        }*/
                    }
                }
            }

        } else if (type instanceof UnsignedByteType) {
            int nBytesPerPix = 1;
            stack.setBitDepth(8);
        } else {
            throw new UnsupportedOperationException("Type "+type.getClass()+" unsupported.");
        }

        return stack;
    }

    /*@Override
    public static <T> void load(final SingleCellArrayImg<T, ?> cell) {

        for (Cursor<T> s = Views.flatIterable(Views.interval(source, cell)).cursor(), t = cell.cursor(); s.hasNext();) {

            t.next().set(s.next());
        }
    }*/

}
