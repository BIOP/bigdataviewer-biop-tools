package bdv.util;

import net.imglib2.*;
import net.imglib2.type.numeric.NumericType;

public class ZerosRAI<T extends NumericType> implements RandomAccessibleInterval<T> {
    T t;

    long[] dimensions;

    RandomAccess zerosRandomAccess;

    public ZerosRAI(T typeInstance, long[] dimensions) {
        this.t = typeInstance;
        t.setZero();
        this.dimensions = dimensions;
        this.zerosRandomAccess = new ZerosRandomAccess();
    }

    @Override
    public long min(int d) {
        return 0;
    }

    @Override
    public long max(int d) {
        return dimensions[d];
    }

    @Override
    public RandomAccess<T> randomAccess() {
        return zerosRandomAccess;
    }

    @Override
    public RandomAccess<T> randomAccess(Interval interval) {
        return zerosRandomAccess;
    }

    @Override
    public int numDimensions() {
        return dimensions.length;
    }

    public class ZerosRandomAccess implements RandomAccess<T> {

        @Override
        public RandomAccess<T> copyRandomAccess() {
            return new ZerosRandomAccess();
        }

        @Override
        public long getLongPosition(int d) {
            return d;
        }

        @Override
        public void fwd(int d) {

        }

        @Override
        public void bck(int d) {

        }

        @Override
        public void move(int distance, int d) {

        }

        @Override
        public void move(long distance, int d) {

        }

        @Override
        public void move(Localizable distance) {

        }

        @Override
        public void move(int[] distance) {

        }

        @Override
        public void move(long[] distance) {

        }

        @Override
        public void setPosition(Localizable position) {

        }

        @Override
        public void setPosition(int[] position) {

        }

        @Override
        public void setPosition(long[] position) {

        }

        @Override
        public void setPosition(int position, int d) {

        }

        @Override
        public void setPosition(long position, int d) {

        }

        @Override
        public int numDimensions() {
            return dimensions.length;
        }

        @Override
        public T get() {
            return t;
        }

        @Override
        public Sampler<T> copy() {
            return new Sampler<T>() {
                @Override
                public T get() {
                    return t;
                }

                @Override
                public Sampler<T> copy() {
                    return this;
                }
            };
        }
    }
}
