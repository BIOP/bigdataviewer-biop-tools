package net.imglib2.realtransform;

import java.util.List;

// TODO : move to bigdataviewer-playground
public class RealTransformHelper {
    /**
     * Makes the list accessible because of protected field
     * @param rts
     * @return
     */
    public static List<RealTransform> getTransformSequence(RealTransformSequence rts) {
        return rts.transforms;
    }
    /**
     * Makes the list accessible because of protected field
     * @param irts
     * @return
     */
    public static List<InvertibleRealTransform> getTransformSequence(InvertibleRealTransformSequence irts) {
        return irts.transforms;
    }
}
