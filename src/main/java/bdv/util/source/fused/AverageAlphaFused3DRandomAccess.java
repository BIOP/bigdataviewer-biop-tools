package bdv.util.source.fused;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import java.util.function.Supplier;

public class AverageAlphaFused3DRandomAccess<T extends RealType<T>> implements SubSetFusedRandomAccess<T> {

    long[] position = new long[3];
    final int nRandomAccesses;
    final RandomAccess<T>[] ra_origins;
    final RandomAccess<FloatType>[] ra_origins_alpha;
    final Supplier<T> pixelSupplier;

    final protected T pixel;

    @SuppressWarnings("CopyConstructorMissesField") // That's the point!
    public AverageAlphaFused3DRandomAccess(AverageAlphaFused3DRandomAccess<T> randomAccess) {
        this.nRandomAccesses = randomAccess.nRandomAccesses;
        ra_origins = new RandomAccess[nRandomAccesses];
        ra_origins_alpha = new RandomAccess[nRandomAccesses];
        this.pixelSupplier = randomAccess.pixelSupplier;
        pixel = pixelSupplier.get();

        // necessary ?
        position[0] = randomAccess.getLongPosition(0);
        position[1] = randomAccess.getLongPosition(1);
        position[2] = randomAccess.getLongPosition(2);

        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i] = randomAccess.ra_origins[i].copyRandomAccess();
            ra_origins_alpha[i] = randomAccess.ra_origins_alpha[i].copyRandomAccess();
            ra_origins[i].setPosition(position);
            ra_origins_alpha[i].setPosition(position);
        }

    }

    public AverageAlphaFused3DRandomAccess(RandomAccess<T>[] ra_origins, RandomAccess<FloatType>[] ra_origins_alpha, Supplier<T> pixelSupplier) {
        this.nRandomAccesses = ra_origins.length;
        this.ra_origins = ra_origins;
        this.ra_origins_alpha = ra_origins_alpha;
        this.pixelSupplier = pixelSupplier;
        pixel = pixelSupplier.get();
    }

    public AverageAlphaFused3DRandomAccess(AverageAlphaFused3DRandomAccess<T> randomAccess, boolean[] subset) {
        int nRandomAccesses = 0;
        for (int i = 0; i<subset.length; i++) {
            if (subset[i]) nRandomAccesses++;
        }

        //System.out.println(nRandomAccesses+"/"+randomAccess.nRandomAccesses);

        this.nRandomAccesses = nRandomAccesses;//randomAccess.nRandomAccesses;
        ra_origins = new RandomAccess[nRandomAccesses];
        ra_origins_alpha = new RandomAccess[nRandomAccesses];
        this.pixelSupplier = randomAccess.pixelSupplier;
        pixel = pixelSupplier.get();

        // necessary ?
        position[0] = randomAccess.getLongPosition(0);
        position[1] = randomAccess.getLongPosition(1);
        position[2] = randomAccess.getLongPosition(2);

        int iSource = 0;
        for (int i=0; i<subset.length; i++) {
            if (subset[i]) {
                ra_origins[iSource] = randomAccess.ra_origins[i].copyRandomAccess();
                ra_origins_alpha[iSource] = randomAccess.ra_origins_alpha[i].copyRandomAccess();
                ra_origins[iSource].setPosition(position);
                ra_origins_alpha[iSource].setPosition(position);
                iSource++;
            }
        }
    }

    @Override
    public RandomAccess<T> copyRandomAccess() {
        return copy();
    }

    @Override
    public long getLongPosition(int d) {
        return position[d];
    }

    @Override
    public void fwd(int d) {
        position[d]++;
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].fwd(d);
            ra_origins_alpha[i].fwd(d);
        }
    }

    @Override
    public void bck(int d) {
        position[d]--;
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].bck(d);
            ra_origins_alpha[i].bck(d);
        }
    }

    @Override
    public void move(int distance, int d) {
        position[d]+=distance;
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].move(distance, d);
            ra_origins_alpha[i].move(distance, d);
        }
    }

    @Override
    public void move(long distance, int d) {
        position[d]+=distance;
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].move(distance, d);
            ra_origins_alpha[i].move(distance, d);
        }
    }

    @Override
    public void move(Localizable distance) {
        position[0]+=distance.getLongPosition(0);
        position[1]+=distance.getLongPosition(1);
        position[2]+=distance.getLongPosition(2);
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].move(distance);
            ra_origins_alpha[i].move(distance);
        }
    }

    @Override
    public void move(int[] distance) {
        position[0]+=distance[0];
        position[1]+=distance[1];
        position[2]+=distance[2];
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].move(distance);
            ra_origins_alpha[i].move(distance);
        }
    }

    @Override
    public void move(long[] distance) {
        position[0]+=distance[0];
        position[1]+=distance[1];
        position[2]+=distance[2];
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].move(distance);
            ra_origins_alpha[i].move(distance);
        }
    }

    @Override // the one to test!!
    public void setPosition(Localizable position) {
        long pX = position.getLongPosition(0);
        long pY = position.getLongPosition(1);
        long pZ = position.getLongPosition(2);
        if ((pX-this.position[0]==1)&&(pY==this.position[1])&&(pZ==this.position[2])) {
            fwd(0);
        } else {
            this.position[0] = pX;
            this.position[1] = pY;
            this.position[2] = pZ;
            for (int i = 0; i < nRandomAccesses; i++) {
                ra_origins[i].setPosition(position);
                ra_origins_alpha[i].setPosition(position);
            }
        }
    }

    @Override
    public void setPosition(int[] position) {
        this.position[0] = position[0];
        this.position[1] = position[1];
        this.position[2] = position[2];
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].setPosition(position);
            ra_origins_alpha[i].setPosition(position);
        }
    }

    @Override
    public void setPosition(long[] position) {
        this.position[0] = position[0];
        this.position[1] = position[1];
        this.position[2] = position[2];
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].setPosition(position);
            ra_origins_alpha[i].setPosition(position);
        }
    }

    @Override
    public void setPosition(int position, int d) {
        this.position[d] = position;
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].setPosition(position, d);
            ra_origins_alpha[i].setPosition(position, d);
        }
    }

    @Override
    public void setPosition(long position, int d) {
        this.position[d] = position;
        for (int i=0; i<nRandomAccesses; i++) {
            ra_origins[i].setPosition(position, d);
            ra_origins_alpha[i].setPosition(position, d);
        }
    }

    @Override
    public int numDimensions() {
        return 3;
    }

    @Override
    public T get() {
        float sum_alpha = 0;
        float value = 0;
        for (int i=0; i<nRandomAccesses; i++) {
            float alpha_v=ra_origins_alpha[i].get().get();
            if (alpha_v!=0) {
                value+=alpha_v*ra_origins[i].get().getRealFloat();
                sum_alpha+=alpha_v;
            }
        }
        pixel.setReal(value/sum_alpha);
        return pixel;
    }

    @Override
    public AverageAlphaFused3DRandomAccess<T> copy() {
       return new AverageAlphaFused3DRandomAccess<T>(this);
    }

    @Override
    public AverageAlphaFused3DRandomAccess<T> copy(boolean[] subset) {
       return new AverageAlphaFused3DRandomAccess<T>(this, subset);
    }
}
