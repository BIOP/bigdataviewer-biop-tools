/*-
 * #%L
 * BigDataViewer tools and plugins for Fiji: fusion, deconvolution, registration and data processing.
 * %%
 * Copyright (C) 2018 - 2026 EPFL BIOP and University of Geneva
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.util.source.field;

import net.imglib2.AbstractEuclideanSpace;
import net.imglib2.Localizable;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.DoubleAccess;
import net.imglib2.img.basictypeaccess.FloatAccess;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.type.Index;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.util.Fraction;

public class NativeRealPoint extends AbstractEuclideanSpace implements RealPositionable, RealLocalizable, NativeType<NativeRealPoint> {

    final NativeTypeFactory<NativeRealPoint, FloatAccess> typeFactory;

    final protected NativeImg< ?, ? extends FloatAccess> img;

    // the DataAccess that holds the information
    protected FloatAccess dataAccess;

    private final Index i;

    /**
     * @param n number of dimensions.
     */
    public NativeRealPoint(int n) {
        super(n);
        i = new Index();
        img = null;
        dataAccess = new FloatArray(n);
        typeFactory = NativeTypeFactory.FLOAT( (a) -> new NativeRealPoint(n, a) );
    }

    public NativeRealPoint(int n, NativeImg<?,? extends FloatAccess> a) {
        super(n);
        i = new Index();
        img = a;
        typeFactory = NativeTypeFactory.FLOAT( (access) -> new NativeRealPoint(n, access) );
    }

    public NativeRealPoint(NativeRealPoint nativeRealPoint) {
        super(nativeRealPoint.n);
        this.set(nativeRealPoint);
        i = new Index();
        img = null;
        dataAccess = new FloatArray(n);
        typeFactory = NativeTypeFactory.FLOAT( (a) -> new NativeRealPoint(n, a) );
    }

    @Override
    public double getDoublePosition(int d) {
        return dataAccess.getValue( i.get() * n + d );
    }

    @Override
    public float getFloatPosition(int d) {
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
            dataAccess.setValue(offset+d, c.getFloatPosition(d));
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

    public void mul(float alpha) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, alpha * getFloatPosition(d));
        }
    }

    //--------------- Realpositionable method

    @Override
    public void move(float distance, int d) {
        dataAccess.setValue(i.get()*n+d, distance + getFloatPosition(d));
    }

    @Override
    public void move(double distance, int d) {
        dataAccess.setValue(i.get()*n+d, (float) distance + getFloatPosition(d));
    }

    @Override
    public void move(RealLocalizable distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance.getFloatPosition(d) + getFloatPosition(d));
        }
    }

    @Override
    public void move(float[] distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance[d] + getFloatPosition(d));
        }
    }

    @Override
    public void move(double[] distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, (float) distance[d] + getFloatPosition(d));
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
            dataAccess.setValue(offset+d, (float) position[d]);
        }
    }

    @Override
    public void setPosition(float position, int d) {
        dataAccess.setValue(i.get()*n+d, position);
    }

    @Override
    public void setPosition(double position, int d) {
        dataAccess.setValue(i.get()*n+d, (float) position);
    }

    public void move(NativeRealPoint point) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, point.getFloatPosition(d) + getFloatPosition(d));
        }
    }

    @Override
    public void fwd(int d) {
        dataAccess.setValue(i.get()*n+d, getFloatPosition(d) + 1);
    }

    @Override
    public void bck(int d) {
        dataAccess.setValue(i.get()*n+d, getFloatPosition(d) - 1);
    }

    @Override
    public void move(int distance, int d) {
        dataAccess.setValue(i.get()*n+d, distance + getFloatPosition(d));
    }

    @Override
    public void move(long distance, int d) {
        dataAccess.setValue(i.get()*n+d, distance + getFloatPosition(d));
    }

    @Override
    public void move(Localizable distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance.getLongPosition(d) + getFloatPosition(d));
        }
    }

    @Override
    public void move(int[] distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance[d] + getFloatPosition(d));
        }
    }

    @Override
    public void move(long[] distance) {
        int offset = i.get()*n;
        for (int d = 0; d < n; d++) {
            dataAccess.setValue(offset+d, distance[d] + getFloatPosition(d));
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
