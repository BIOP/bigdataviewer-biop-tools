package bdv.util.source.field;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Localizable;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.DoubleAccess;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.type.Index;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.util.Fraction;

public class NativeRealPoint extends AbstractEuclideanSpace implements RealPositionable, RealLocalizable, NativeType<NativeRealPoint> {

    final NativeTypeFactory<NativeRealPoint, DoubleAccess> typeFactory;

    final protected NativeImg< ?, ? extends DoubleAccess> img;

    // the DataAccess that holds the information
    protected DoubleAccess dataAccess;

    private final Index i;

    /**
     * @param n number of dimensions.
     */
    public NativeRealPoint(int n) {
        super(n);
        i = new Index();
        img = null;
        dataAccess = new DoubleArray(n);
        typeFactory = NativeTypeFactory.DOUBLE( (a) -> new NativeRealPoint(n, a) );
    }

    public NativeRealPoint(int n, NativeImg<?,? extends DoubleAccess> a) {
        super(n);
        i = new Index();
        img = a;
        typeFactory = NativeTypeFactory.DOUBLE( (access) -> new NativeRealPoint(n, access) );
    }

    public NativeRealPoint(NativeRealPoint nativeRealPoint) {
        super(nativeRealPoint.n);
        this.set(nativeRealPoint);
        i = new Index();
        img = null;
        dataAccess = new DoubleArray(n);
        typeFactory = NativeTypeFactory.DOUBLE( (a) -> new NativeRealPoint(n, a) );
    }


    @Override
    public double getDoublePosition(int d) {
        return dataAccess.getValue( i.get() * n + d );
    }

    @Override
    public Fraction getEntitiesPerPixel() {
        return new Fraction(n,1);
    }

    @Override
    public NativeRealPoint duplicateTypeOnSameNativeImg() {
        return new NativeRealPoint(n, img );
    }

    @Override
    public NativeTypeFactory<NativeRealPoint, ?> getNativeTypeFactory() {
        return typeFactory;
    }

    @Override
    public void updateContainer(Object c) {
        dataAccess = img.update( c );
    }

    @Override
    public Index index() {
        return i;
    }

    @Override
    public NativeRealPoint createVariable() {
        return new NativeRealPoint(n);
    }

    @Override
    public NativeRealPoint copy() {
        return new NativeRealPoint(this);
    }

    @Override
    public void set(NativeRealPoint c) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, c.getDoublePosition(d));
        }
    }

    @Override
    public boolean valueEquals(NativeRealPoint nativeRealPoint) {
        for (int d = 0; d < n; d++) {
            if (getDoublePosition(d) != nativeRealPoint.getDoublePosition(d)) {
                return false;
            }
        }
        return true;
    }

    public void setPosition(NativeRealPoint nativeRealPoint) {
        set(nativeRealPoint);
    }

    public void mul(double alpha) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, alpha * getDoublePosition(d));
        }
    }

    //--------------- Realpositionable method

    @Override
    public void move(float distance, int d) {
        dataAccess.setValue(i.get()*n+d, distance + getDoublePosition(d));
    }

    @Override
    public void move(double distance, int d) {
        dataAccess.setValue(i.get()*n+d, distance + getDoublePosition(d));
    }

    @Override
    public void move(RealLocalizable distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance.getDoublePosition(d) + getDoublePosition(d));
        }
    }

    @Override
    public void move(float[] distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance[d] + getDoublePosition(d));
        }
    }

    @Override
    public void move(double[] distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance[d] + getDoublePosition(d));
        }
    }

    @Override
    public void setPosition(RealLocalizable position) {
        setPosition(positionAsDoubleArray());
    }

    public void setPosition(float[] position) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, position[d]);
        }
    }

    @Override
    public void setPosition(double[] position) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, position[d]);
        }
    }

    @Override
    public void setPosition(float position, int d) {
        dataAccess.setValue(i.get()*n+d, position);
    }

    @Override
    public void setPosition(double position, int d) {
        dataAccess.setValue(i.get()*n+d, position);
    }

    public void move(NativeRealPoint point) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, point.getDoublePosition(d) + getDoublePosition(d));
        }
    }

    @Override
    public void fwd(int d) {
        dataAccess.setValue(i.get()*n+d, getDoublePosition(d) + 1);
    }

    @Override
    public void bck(int d) {
        dataAccess.setValue(i.get()*n+d, getDoublePosition(d) - 1);
    }

    @Override
    public void move(int distance, int d) {
        dataAccess.setValue(i.get()*n+d, distance + getDoublePosition(d));
    }

    @Override
    public void move(long distance, int d) {
        dataAccess.setValue(i.get()*n+d, distance + getDoublePosition(d));
    }

    @Override
    public void move(Localizable distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance.getLongPosition(d) + getDoublePosition(d));
        }
    }

    @Override
    public void move(int[] distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance[d] + getDoublePosition(d));
        }
    }

    @Override
    public void move(long[] distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance[d] + getDoublePosition(d));
        }
    }

    @Override
    public void setPosition(Localizable position) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, position.getLongPosition(d));
        }
    }

    @Override
    public void setPosition(int[] position) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, position[d]);
        }
    }

    @Override
    public void setPosition(long[] position) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, position[d]);
        }
    }

    @Override
    public void setPosition(int position, int d) {
        dataAccess.setValue(i.get()*n+d, position);
    }

    @Override
    public void setPosition(long position, int d) {
        dataAccess.setValue(i.get()*n+d, position);
    }
}
