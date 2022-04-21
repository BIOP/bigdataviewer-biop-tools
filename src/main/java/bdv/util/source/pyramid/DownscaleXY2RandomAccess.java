package bdv.util.source.pyramid;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.view.Views;

import java.util.function.Supplier;

public class DownscaleXY2RandomAccess< T extends NumericType<T> & NativeType<T>> implements RandomAccess<T> {

    long[] position = new long[3];
    final RandomAccess<T> ra;
    //final RandomAccessible<T> x0y0;
    /*final RandomAccessible<T> x1y0;
    final RandomAccessible<T> x0y1;
    final RandomAccessible<T> x1y1;*/

    final protected T pixel;
    final Supplier<T> pixelSupplier;

    public DownscaleXY2RandomAccess(RandomAccess<T> ra, RandomAccessible<T> rable, Supplier<T> pixelSupplier) {
        this.ra = ra.copyRandomAccess();
        this.pixelSupplier = pixelSupplier;
        pixel = pixelSupplier.get();
        //Views.translate()
        //this.x0y0 = Views.translate(ra, 1,0,0);
        //FinalInterval i0 = new FinalInterval(new long[]{0,0,0}, new long[]{rai.max(0)-1, source.max(1)-1, source.max(2)});
        //FinalInterval ix1 = new FinalInterval(new long[]{1,0,0}, new long[]{source.max(0), source.max(1), source.max(2)});
        //FinalInterval iy1 = new FinalInterval(new long[]{0,1,0}, new long[]{source.max(0), source.max(1), source.max(2)});
        /*FinalInterval ix1y1 = new FinalInterval(new long[]{1,1,0}, new long[]{source.max(0), source.max(1), source.max(2)});
        RandomAccessibleInterval<T> x0 = Views.interval(source, i0);
        RandomAccessibleInterval<T> xp1 = Views.interval(source, ix1);
        RandomAccessibleInterval<T> yp1 = Views.interval(source, iy1);
        RandomAccessibleInterval<T> xp1yp1 = Views.interval(source, ix1y1);*/
    }

    @Override
    public RandomAccess<T> copyRandomAccess() {
        return new DownscaleXY2RandomAccess<>(ra, pixelSupplier);
    }

    @Override
    public long getLongPosition(int d) {
        return position[d];
    }

    @Override
    public void fwd(int d) {
        position[d]++;
    }

    @Override
    public void bck(int d) {
        position[d]--;
    }

    @Override
    public void move(int distance, int d) {
        position[d]+=distance;
    }

    @Override
    public void move(long distance, int d) {
        position[d]+=distance;
    }

    @Override
    public void move(Localizable distance) {
        position[0]+=distance.getLongPosition(0);
        position[1]+=distance.getLongPosition(1);
        position[2]+=distance.getLongPosition(2);
    }

    @Override
    public void move(int[] distance) {
        position[0]+=distance[0];
        position[1]+=distance[1];
        position[2]+=distance[2];
    }

    @Override
    public void move(long[] distance) {
        position[0]+=distance[0];
        position[1]+=distance[1];
        position[2]+=distance[2];
    }

    @Override
    public void setPosition(Localizable position) {
        this.position[0]=position.getLongPosition(0);
        this.position[1]=position.getLongPosition(1);
        this.position[2]=position.getLongPosition(2);
    }

    @Override
    public void setPosition(int[] position) {
        this.position[0] = position[0];
        this.position[1] = position[1];
        this.position[2] = position[2];
    }

    @Override
    public void setPosition(long[] position) {
        this.position[0] = position[0];
        this.position[1] = position[1];
        this.position[2] = position[2];
    }

    @Override
    public void setPosition(int position, int d) {
        this.position[d] = position;
    }

    @Override
    public void setPosition(long position, int d) {
        this.position[d] = position;
    }

    @Override
    public int numDimensions() {
        return 3;
    }

    @Override
    public T get() {
        return ;
    }

    @Override
    public Sampler<T> copy() {
        return new DownscaleXY2RandomAccess<>(ra, pixelSupplier);
    }
}
