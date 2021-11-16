package net.imglib2.realtransform;

import java.util.List;

// TODO : move to bigdataviewer-playground
public class RealTransformHelper {

    public static List<RealTransform> getTransformSequence(RealTransformSequence rts) {
        return rts.transforms;
    }

    public static List<InvertibleRealTransform> getTransformSequence(InvertibleRealTransformSequence irts) {
        return irts.transforms;
    }
}
