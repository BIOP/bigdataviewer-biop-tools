package bdv.util.source.fused;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Fuses sources by taking, at each voxel, the <b>median</b> of the sources present there.
 * <p>
 * Unlike {@link AverageAlphaFused3DRandomAccess}, the alpha is used as a presence mask only: a
 * source contributes one value to the median if its alpha is non-zero, whatever the alpha's
 * magnitude. There is no meaningful weighted median for smoothly blended overlaps, so this mode is
 * meant for the case where alpha is binary - many sources covering the same region, of which the
 * typical value is wanted rather than the mean. Averaging bead crops into a PSF is the motivating
 * example: a crop that happens to contain a second bead is an outlier at the voxels where that
 * neighbour falls, and the median discards it instead of spreading it over the result.
 * </p>
 * <p>
 * The values of the present sources are gathered into a reusable buffer and sorted, so the cost per
 * voxel grows as {@code n log n} with the number of overlapping sources. For an even count the two
 * middle values are averaged.
 * </p>
 */
public class MedianAlphaFused3DRandomAccess<T extends RealType<T>> implements SubSetFusedRandomAccess<T> {

    long[] position = new long[3];
    final int nRandomAccesses;
    final RandomAccess<T>[] ra_origins;
    final RandomAccess<FloatType>[] ra_origins_alpha;
    final Supplier<T> pixelSupplier;

    /** Reused across calls to {@link #get()}: one random access is used by a single thread. */
    final private float[] values;

    final protected T pixel;

    @SuppressWarnings("CopyConstructorMissesField") // That's the point!
    public MedianAlphaFused3DRandomAccess(MedianAlphaFused3DRandomAccess<T> randomAccess) {
        this.nRandomAccesses = randomAccess.nRandomAccesses;
        ra_origins = new RandomAccess[nRandomAccesses];
        ra_origins_alpha = new RandomAccess[nRandomAccesses];
        this.pixelSupplier = randomAccess.pixelSupplier;
        pixel = pixelSupplier.get();
        values = new float[nRandomAccesses];

        position[0] = randomAccess.getLongPosition(0);
        position[1] = randomAccess.getLongPosition(1);
        position[2] = randomAccess.getLongPosition(2);

        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i] = randomAccess.ra_origins[i].copyRandomAccess();
            ra_origins_alpha[i] = randomAccess.ra_origins_alpha[i].copyRandomAccess();
            ra_origins[i].setPosition(position);
            ra_origins_alpha[i].setPosition(position);
        }
    }

    public MedianAlphaFused3DRandomAccess(RandomAccess<T>[] ra_origins,
                                          RandomAccess<FloatType>[] ra_origins_alpha,
                                          Supplier<T> pixelSupplier) {
        this.nRandomAccesses = ra_origins.length;
        this.ra_origins = ra_origins;
        this.ra_origins_alpha = ra_origins_alpha;
        this.pixelSupplier = pixelSupplier;
        this.pixel = pixelSupplier.get();
        this.values = new float[nRandomAccesses];
    }

    public MedianAlphaFused3DRandomAccess(MedianAlphaFused3DRandomAccess<T> randomAccess, boolean[] subset) {
        int nRandomAccesses = 0;
        for (boolean present : subset) {
            if (present) nRandomAccesses++;
        }

        this.nRandomAccesses = nRandomAccesses;
        ra_origins = new RandomAccess[nRandomAccesses];
        ra_origins_alpha = new RandomAccess[nRandomAccesses];
        this.pixelSupplier = randomAccess.pixelSupplier;
        pixel = pixelSupplier.get();
        values = new float[nRandomAccesses];

        position[0] = randomAccess.getLongPosition(0);
        position[1] = randomAccess.getLongPosition(1);
        position[2] = randomAccess.getLongPosition(2);

        int iSource = 0;
        for (int i = 0; i < subset.length; i++) {
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
        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i].fwd(d);
            ra_origins_alpha[i].fwd(d);
        }
    }

    @Override
    public void bck(int d) {
        position[d]--;
        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i].bck(d);
            ra_origins_alpha[i].bck(d);
        }
    }

    @Override
    public void move(int distance, int d) {
        position[d] += distance;
        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i].move(distance, d);
            ra_origins_alpha[i].move(distance, d);
        }
    }

    @Override
    public void move(long distance, int d) {
        position[d] += distance;
        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i].move(distance, d);
            ra_origins_alpha[i].move(distance, d);
        }
    }

    @Override
    public void move(Localizable distance) {
        position[0] += distance.getLongPosition(0);
        position[1] += distance.getLongPosition(1);
        position[2] += distance.getLongPosition(2);
        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i].move(distance);
            ra_origins_alpha[i].move(distance);
        }
    }

    @Override
    public void move(int[] distance) {
        position[0] += distance[0];
        position[1] += distance[1];
        position[2] += distance[2];
        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i].move(distance);
            ra_origins_alpha[i].move(distance);
        }
    }

    @Override
    public void move(long[] distance) {
        position[0] += distance[0];
        position[1] += distance[1];
        position[2] += distance[2];
        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i].move(distance);
            ra_origins_alpha[i].move(distance);
        }
    }

    @Override
    public void setPosition(Localizable position) {
        long pX = position.getLongPosition(0);
        long pY = position.getLongPosition(1);
        long pZ = position.getLongPosition(2);
        if ((pX - this.position[0] == 1) && (pY == this.position[1]) && (pZ == this.position[2])) {
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
        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i].setPosition(position);
            ra_origins_alpha[i].setPosition(position);
        }
    }

    @Override
    public void setPosition(long[] position) {
        this.position[0] = position[0];
        this.position[1] = position[1];
        this.position[2] = position[2];
        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i].setPosition(position);
            ra_origins_alpha[i].setPosition(position);
        }
    }

    @Override
    public void setPosition(int position, int d) {
        this.position[d] = position;
        for (int i = 0; i < nRandomAccesses; i++) {
            ra_origins[i].setPosition(position, d);
            ra_origins_alpha[i].setPosition(position, d);
        }
    }

    @Override
    public void setPosition(long position, int d) {
        this.position[d] = position;
        for (int i = 0; i < nRandomAccesses; i++) {
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
        int n = 0;
        for (int i = 0; i < nRandomAccesses; i++) {
            if (ra_origins_alpha[i].get().get() != 0) {
                values[n++] = ra_origins[i].get().getRealFloat();
            }
        }
        if (n == 0) {
            pixel.setZero();
            return pixel;
        }
        Arrays.sort(values, 0, n);
        // Even counts average the two middle values, so the result does not jump when a source
        // appears or disappears at the edge of a crop.
        pixel.setReal((n & 1) == 1
                ? values[n >> 1]
                : 0.5 * ((double) values[(n >> 1) - 1] + (double) values[n >> 1]));
        return pixel;
    }

    @Override
    public MedianAlphaFused3DRandomAccess<T> copy() {
        return new MedianAlphaFused3DRandomAccess<>(this);
    }

    @Override
    public MedianAlphaFused3DRandomAccess<T> copy(boolean[] subset) {
        return new MedianAlphaFused3DRandomAccess<>(this, subset);
    }
}
