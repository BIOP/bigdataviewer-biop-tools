import bdv.util.RandomAccessibleIntervalSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.source.affine.AffineEditor;
import net.imagej.ImageJ;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.display.BrightnessAdjuster;
import sc.fiji.bdvpg.source.display.ColorChanger;

/**
 * Opens the {@link AffineEditor} on two letters F, the moving one rotated and shifted, and prints the result.
 */
public class AffineEditorDemo {

    public static void main(String... args) {
        new ImageJ();
        AffineTransform3D offset = new AffineTransform3D();
        offset.rotate(2, 0.4);
        offset.translate(30, -20, 0);
        SourceAndConverter<?>[] fixed = {letterF("fixed", new AffineTransform3D(), new ARGBType(ARGBType.rgba(0, 255, 0, 255)))};
        SourceAndConverter<?>[] moving = {letterF("moving", offset, new ARGBType(ARGBType.rgba(255, 0, 255, 255)))};
        AffineTransform3D result = AffineEditor.edit(fixed, moving, new AffineTransform3D(), null, 0, "Affine editor demo");
        System.out.println(result == null ? "Cancelled" : "Applied: " + result);
        System.exit(0);
    }

    /** A 200 x 200 image of the letter F, which shows rotations and mirrors */
    public static SourceAndConverter<?> letterF(String name, AffineTransform3D transform, ARGBType color) {
        ArrayImg<UnsignedByteType, ByteArray> img = ArrayImgs.unsignedBytes(200, 200, 1);
        byte[] pixels = img.update(null).getCurrentStorageArray();
        for (int y = 30; y < 170; y++) {
            for (int x = 50; x < 150; x++) {
                boolean stem = x < 80, top = y < 60, middle = (y >= 90) && (y < 115) && (x < 130);
                if (stem || top || middle) pixels[y * 200 + x] = (byte) 255;
            }
        }
        SourceAndConverter<?> source = SourceHelper.createSourceAndConverter(
                new RandomAccessibleIntervalSource<>(img, new UnsignedByteType(), transform, name));
        new ColorChanger(source, color).run();
        new BrightnessAdjuster(source, 0, 255).run();
        return source;
    }

}
