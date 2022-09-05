package ch.epfl.biop.sourceandconverter.processor;

import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterAndTimeRange;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceTransformHelper;

import java.util.Arrays;
import java.util.stream.Collectors;

public class SourcesAffineTransformer implements SourcesProcessor {

    final public AffineTransform3D at3d;

    public SourcesAffineTransformer(AffineTransform3D at3d) {
        this.at3d = at3d.copy();
    }

    @Override
    public SourceAndConverter<?>[] apply(SourceAndConverter<?>[] sourceAndConverters) {
        SourceAndConverter<?>[] out = new SourceAndConverter<?>[sourceAndConverters.length];
        for (int i = 0;i<out.length;i++) {
            out[i] = SourceTransformHelper.createNewTransformedSourceAndConverter(at3d, new SourceAndConverterAndTimeRange(sourceAndConverters[i], 0));
        }
        return out;
    }

    public String toString() {
        return "M";
    }
}
