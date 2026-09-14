import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.source.affine.AffineEditor;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the {@link AffineEditor} on four pairs of letters F, each moving letter rotated and shifted differently,
 * and prints the results.
 */
public class AffineEditorPairsDemo {

    public static void main(String... args) {
        new ImageJ();
        SourceAndConverter<?>[] fixed = {AffineEditorDemo.letterF("fixed", new AffineTransform3D(), new ARGBType(ARGBType.rgba(0, 255, 0, 255)))};
        List<AffineEditor.Pair> pairs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            AffineTransform3D offset = new AffineTransform3D();
            offset.rotate(2, 0.1 * (i + 1));
            offset.translate(10 * i, -10 * i, 0);
            SourceAndConverter<?>[] moving = {AffineEditorDemo.letterF("moving " + i, offset, new ARGBType(ARGBType.rgba(255, 0, 255, 255)))};
            pairs.add(new AffineEditor.Pair(fixed, moving, new AffineTransform3D(), new double[]{0, 0, 200, 200}, "letter " + i));
        }
        List<AffineTransform3D> result = AffineEditor.edit(pairs, 0, "Affine editor pairs demo");
        System.out.println(result == null ? "Cancelled" : "Applied: " + result);
        System.exit(0);
    }

}
