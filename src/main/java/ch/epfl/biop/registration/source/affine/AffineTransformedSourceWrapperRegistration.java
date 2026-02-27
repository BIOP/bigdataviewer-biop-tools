package ch.epfl.biop.registration.source.affine;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.source.SourceAndTimeRange;
import sc.fiji.bdvpg.source.transform.SourceTransformHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake registration which simply wraps a transform which is used to modify either:
 * - the place of the image in the user GUI, or to
 * TODO : write a bit more clearly what this does
 */
public class AffineTransformedSourceWrapperRegistration extends AffineTransformSourceRegistration {

    Map<SourceAndConverter<?>, SourceAndConverter<?>> alreadyTransformedSources = new ConcurrentHashMap<>();

    @Override
    public boolean register() {
        isDone = true;
        return true;
    }

    /**
     * These function are kept in order to avoid serializing many times
     * unnecessary affinetransform
     * @param at3d_in affine transform
     */
    public synchronized void setAffineTransform(AffineTransform3D at3d_in) {
        this.at3d = at3d_in;
        alreadyTransformedSources.keySet().forEach(sac -> SourceTransformHelper.set(at3d_in, new SourceAndTimeRange<>(alreadyTransformedSources.get(sac), timePoint)));
    }

    /**
     * Returns a copy of the current affine transform.
     *
     * @return a copy of the affine transform
     */
    public AffineTransform3D getAffineTransform() {
        return at3d.copy();
    }

    /**
     * Overriding to actually mutate SourceAndConverter,
     * it's the only registration which does that, because
     * it's actually not really a registration
     * @param img image
     * @return mutates the transform
     */
    @Override
    public SourceAndConverter<?>[] getTransformedImageMovingToFixed(SourceAndConverter<?>[] img) {

        SourceAndConverter<?>[] out = new SourceAndConverter[img.length];

        for (int idx = 0;idx<img.length;idx++) {
            if (alreadyTransformedSources.containsKey(img[idx])) {
                out[idx] = alreadyTransformedSources.get(img[idx]);
                SourceTransformHelper.set(at3d, new SourceAndTimeRange<>(out[idx], timePoint));
            } else {
                out[idx] = SourceTransformHelper.createNewTransformedSourceAndConverter(at3d, new SourceAndTimeRange<>(img[idx], timePoint));
                alreadyTransformedSources.put(img[idx], out[idx]);
            }
        }

        return out;
    }

    @Override
    public void abort() {

    }

}
