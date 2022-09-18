package bdv.util.source.field;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Localizable;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.Volatile;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.FloatAccess;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.Index;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.util.Fraction;

public class NativeRealPoint3D //extends AbstractEuclideanSpace
        implements RealPositionable, RealLocalizable, NativeType<NativeRealPoint3D> {//}, Volatile<NativeRealPoint3D> {

    private static final NativeTypeFactory<NativeRealPoint3D, FloatAccess> typeFactory = NativeTypeFactory.FLOAT( NativeRealPoint3D::new );

    final protected NativeImg< ?, ? extends FloatAccess> img;

    // the DataAccess that holds the information
    protected FloatAccess dataAccess;

    private final Index i;

    public NativeRealPoint3D() {
        i = new Index();
        img = null;
        dataAccess = new FloatArray(3);
    }

    public NativeRealPoint3D(NativeImg<?,? extends FloatAccess> a) {
        i = new Index();
        img = a;
    }

    public NativeRealPoint3D(NativeRealPoint3D nativeRealPoint) {
        this.set(nativeRealPoint);
        i = new Index();
        img = null;
        dataAccess = new FloatArray(4);
    }

    @Override
    public double getDoublePosition(int d) {
        return dataAccess.getValue( (i.get() << 2) + d );
    }

    @Override
    public float getFloatPosition(int d) {
        return dataAccess.getValue( (i.get() << 2) + d );
    }

    @Override
    public Fraction getEntitiesPerPixel() {
        return new Fraction(4,1);
    }

    @Override
    public NativeRealPoint3D duplicateTypeOnSameNativeImg() {
        return new NativeRealPoint3D(img );
    }

    @Override
    public NativeTypeFactory<NativeRealPoint3D, ?> getNativeTypeFactory() {
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
    public NativeRealPoint3D createVariable() {
        return new NativeRealPoint3D();
    }

    @Override
    public NativeRealPoint3D copy() {
        return new NativeRealPoint3D(this);
    }

    @Override
    public void set(NativeRealPoint3D c) {
        int offsetTarget = i.get() << 2;
        int offsetSource = c.i.get() << 2;
        FloatAccess sourceAccess = c.dataAccess;
        dataAccess.setValue(offsetTarget+0, sourceAccess.getValue(offsetSource+0));
        dataAccess.setValue(offsetTarget+1, sourceAccess.getValue(offsetSource+1));
        dataAccess.setValue(offsetTarget+2, sourceAccess.getValue(offsetSource+2));
    }

    @Override
    public boolean valueEquals(NativeRealPoint3D nativeRealPoint) {
        for (int d = 0; d < 3; d++) {
            if (getDoublePosition(d) != nativeRealPoint.getDoublePosition(d)) {
                return false;
            }
        }
        return true;
    }

    public void setPosition(NativeRealPoint3D nativeRealPoint) {
        set(nativeRealPoint);
    }

    public void mul(float alpha) {
        int offset = i.get() << 2;
        dataAccess.setValue(offset+0, alpha * dataAccess.getValue(offset+0));
        dataAccess.setValue(offset+1, alpha * dataAccess.getValue(offset+1));
        dataAccess.setValue(offset+2, alpha * dataAccess.getValue(offset+2));
    }

    //--------------- Realpositionable method

    @Override
    public void move(float distance, int d) {
        int offset = (i.get() << 2)+d;
        dataAccess.setValue(offset, distance + dataAccess.getValue(offset));
    }

    @Override
    public void move(double distance, int d) {
        int offset = (i.get() << 2)+d;
        dataAccess.setValue(offset, (float) distance + dataAccess.getValue(offset));
    }

    public void move(NativeRealPoint3D point) {
        int offsetTarget = i.get() << 2;
        int offsetSource = point.i.get() << 2;
        FloatAccess sourceAccess = point.dataAccess;
        dataAccess.setValue(offsetTarget+0, dataAccess.getValue(offsetTarget+0) + sourceAccess.getValue(offsetSource+0));
        dataAccess.setValue(offsetTarget+1, dataAccess.getValue(offsetTarget+1) + sourceAccess.getValue(offsetSource+1));
        dataAccess.setValue(offsetTarget+2, dataAccess.getValue(offsetTarget+2) + sourceAccess.getValue(offsetSource+2));

    }

    @Override
    public void move(RealLocalizable distance) {
        int offset = i.get() << 2;
        dataAccess.setValue(offset+0, dataAccess.getValue(offset+0) + distance.getFloatPosition(0));
        dataAccess.setValue(offset+1, dataAccess.getValue(offset+1) + distance.getFloatPosition(1));
        dataAccess.setValue(offset+2, dataAccess.getValue(offset+2) + distance.getFloatPosition(2));
    }

    @Override
    public void move(float[] distance) {
        int offset = i.get() << 2;
        dataAccess.setValue(offset+0, dataAccess.getValue(offset+0) + distance[0]);
        dataAccess.setValue(offset+1, dataAccess.getValue(offset+1) + distance[1]);
        dataAccess.setValue(offset+2, dataAccess.getValue(offset+2) + distance[2]);
    }

    @Override
    public void move(double[] distance) {
        int offset = i.get() << 2;
        dataAccess.setValue(offset+0, dataAccess.getValue(offset+0) + (float) distance[0]);
        dataAccess.setValue(offset+1, dataAccess.getValue(offset+1) + (float) distance[1]);
        dataAccess.setValue(offset+2, dataAccess.getValue(offset+2) + (float) distance[2]);
    }

    @Override
    public void setPosition(RealLocalizable position) {
        setPosition(positionAsDoubleArray());
    }

    @Override
    public void localize( final double[] position )
    {
        int offset = i.get() << 2;
        position[ 0 ] = dataAccess.getValue(offset+0);
        position[ 1 ] = dataAccess.getValue(offset+1);
        position[ 2 ] = dataAccess.getValue(offset+2);
    }

    @Override
    public double[] positionAsDoubleArray()
    {
        int offset = i.get() << 2;
        return new double[] {
                dataAccess.getValue(offset+0),
                dataAccess.getValue(offset+1),
                dataAccess.getValue(offset+2)
        };
    }

    public void setPosition(float[] position) {
        int offsetTarget = i.get() << 2;
        dataAccess.setValue(offsetTarget+0, position[0]);
        dataAccess.setValue(offsetTarget+1, position[1]);
        dataAccess.setValue(offsetTarget+2, position[2]);
    }

    @Override
    public void setPosition(double[] position) {
        int offsetTarget = i.get() << 2;
        dataAccess.setValue(offsetTarget+0, (float) position[0]);
        dataAccess.setValue(offsetTarget+1, (float) position[1]);
        dataAccess.setValue(offsetTarget+2, (float) position[2]);
    }

    @Override
    public void setPosition(float position, int d) {
        dataAccess.setValue((i.get() << 2)+d, position);
    }

    @Override
    public void setPosition(double position, int d) {
        dataAccess.setValue((i.get() << 2)+d, (float) position);
    }

    @Override
    public void fwd(int d) {
        int offset = (i.get() << 2)+d;
        dataAccess.setValue(offset, dataAccess.getValue(offset) + 1);
    }

    @Override
    public void bck(int d) {
        int offset = (i.get() << 2)+d;
        dataAccess.setValue(offset, dataAccess.getValue(offset) - 1);
    }

    @Override
    public void move(int distance, int d) {
        int offset = (i.get() << 2)+d;
        dataAccess.setValue(offset, dataAccess.getValue(offset) + distance);
    }

    @Override
    public void move(long distance, int d) {
        int offset = (i.get() << 2)+d;
        dataAccess.setValue(offset, dataAccess.getValue(offset) + distance);
    }

    @Override
    public void move(Localizable distance) {
        int offset = i.get() << 2;
        dataAccess.setValue(offset+0, dataAccess.getValue(offset+0) + distance.getFloatPosition(0));
        dataAccess.setValue(offset+1, dataAccess.getValue(offset+1) + distance.getFloatPosition(1));
        dataAccess.setValue(offset+2, dataAccess.getValue(offset+2) + distance.getFloatPosition(2));
    }

    @Override
    public void move(int[] distance) {
        int offset = i.get() << 2;
        dataAccess.setValue(offset+0, dataAccess.getValue(offset+0) + distance[0]);
        dataAccess.setValue(offset+1, dataAccess.getValue(offset+1) + distance[1]);
        dataAccess.setValue(offset+2, dataAccess.getValue(offset+2) + distance[2]);
    }

    @Override
    public void move(long[] distance) {
        int offset = i.get() << 2;
        dataAccess.setValue(offset+0, dataAccess.getValue(offset+0) + distance[0]);
        dataAccess.setValue(offset+1, dataAccess.getValue(offset+1) + distance[1]);
        dataAccess.setValue(offset+2, dataAccess.getValue(offset+2) + distance[2]);
    }

    @Override
    public void setPosition(Localizable position) {
        int offset = i.get() << 2;
        dataAccess.setValue(offset+0, position.getFloatPosition(0));
        dataAccess.setValue(offset+1, position.getFloatPosition(1));
        dataAccess.setValue(offset+2, position.getFloatPosition(2));
    }

    @Override
    public void setPosition(int[] position) {
        int offset = i.get() << 2;
        dataAccess.setValue(offset+0, position[0]);
        dataAccess.setValue(offset+1, position[1]);
        dataAccess.setValue(offset+2, position[2]);
    }

    @Override
    public void setPosition(long[] position) {
        int offset = i.get() << 2;
        dataAccess.setValue(offset+0, position[0]);
        dataAccess.setValue(offset+1, position[1]);
        dataAccess.setValue(offset+2, position[2]);
    }

    @Override
    public void setPosition(int position, int d) {
        dataAccess.setValue((i.get() << 2)+d, position);
    }

    @Override
    public void setPosition(long position, int d) {
        dataAccess.setValue((i.get() << 2)+d, position);
    }

    @Override
    public int numDimensions() {
        return 3;
    }
}
