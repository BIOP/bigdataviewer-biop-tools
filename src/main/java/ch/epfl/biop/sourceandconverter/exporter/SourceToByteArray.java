package ch.epfl.biop.sourceandconverter.exporter;


import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class SourceToByteArray {

    static public byte[] raiARGBToByteArray(RandomAccessibleInterval<ARGBType> rai) {

        return null;
    }

    static public byte[] raiUnsignedByteTypeToByteArray(RandomAccessibleInterval<UnsignedByteType> rai, UnsignedByteType t) {
        Cursor<UnsignedByteType> c = Views.flatIterable(rai).cursor();
        long nBytes = rai.dimension(0)*rai.dimension(1)*rai.dimension(2);

        if (nBytes>Integer.MAX_VALUE) {
            System.err.println("Too many bytes during export!");
            return null;
        }

        byte[] out = new byte[(int) nBytes];

        for (int i=0;i<nBytes;i++) {
            out[i]=c.next().getByte();
        }
        return out;
    }

    public static <T> byte[] raiToByteArray(RandomAccessibleInterval<T> rai, T pixelInstance) {
        long nBytes = rai.dimension(0);
        for (int d=1;d<rai.numDimensions();d++) {
            nBytes*=rai.dimension(d);
        }
        if (pixelInstance instanceof UnsignedByteType) {
            Cursor<UnsignedByteType> c = (Cursor<UnsignedByteType>) Views.flatIterable(rai).cursor();

            nBytes*=1; // Byte

            if (nBytes>Integer.MAX_VALUE) {
                System.err.println("Too many bytes during export!");
                return null;
            }

            byte[] out = new byte[(int) nBytes];

            for (int i=0;i<nBytes;i++) {
                out[i]=c.next().getByte();
            }
            return out;
        } else if (pixelInstance instanceof UnsignedShortType) {
            Cursor<UnsignedShortType> c = (Cursor<UnsignedShortType>) Views.flatIterable(rai).cursor();

            nBytes *=2; // Short

            if (nBytes>Integer.MAX_VALUE) {
                System.err.println("Too many bytes during export!");
                return null;
            }

            byte[] out = new byte[(int) nBytes];

            for (int i=0;i<nBytes;i+=2) {
                int value = c.next().get();
                out[i]=(byte)(value >>> 8);
                out[i+1]=(byte)value;
            }
            return out;
        } else {
            throw new UnsupportedOperationException("Unsupported pixel type of class "+pixelInstance.getClass().getName());
        }
    }
}