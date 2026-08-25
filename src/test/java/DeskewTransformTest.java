import ch.epfl.biop.command.dataset.DatasetDeskewCommand;
import net.imglib2.realtransform.AffineTransform3D;
import org.junit.Assert;
import org.junit.Test;

/**
 * Checks that the LLS7 preset of {@link DatasetDeskewCommand} reproduces the deskew transform
 * which used to be hardcoded in the Zeiss Quick Start CZI opener of bigdataviewer-image-loaders.
 */
public class DeskewTransformTest {

    @Test
    public void lls7PresetMatchesReferenceImplementation() {
        // An arbitrary root transform: anisotropic voxel size and a non zero position
        AffineTransform3D rootTransform = new AffineTransform3D();
        rootTransform.set(
                0.145, 0, 0, 12.5,
                0, 0.145, 0, -37.2,
                0, 0, 0.4, 105.3);

        AffineTransform3D expected = referenceLLS7Transform(rootTransform.copy());
        AffineTransform3D actual = deskewLikeTheCommand(rootTransform.copy());

        double[] expectedValues = expected.getRowPackedCopy();
        double[] actualValues = actual.getRowPackedCopy();
        Assert.assertArrayEquals(expectedValues, actualValues, 1e-10);
    }

    /** The LLS7 preset of the command, applied on top of the root transform. */
    private AffineTransform3D deskewLikeTheCommand(AffineTransform3D rootTransform) {
        AffineTransform3D deskew = DatasetDeskewCommand.getDeskewTransform(
                "Y", "-Z", 30, "Z", true);

        // Same conjugation as the one performed by the command around the image origin
        AffineTransform3D addOffset = new AffineTransform3D();
        addOffset.set(rootTransform.get(0, 3), 0, 3);
        addOffset.set(rootTransform.get(1, 3), 1, 3);
        addOffset.set(rootTransform.get(2, 3), 2, 3);

        deskew.concatenate(addOffset.inverse());
        deskew.preConcatenate(addOffset);

        rootTransform.preConcatenate(deskew);
        return rootTransform;
    }

    /** Verbatim copy of the transform which used to be applied in BioFormatsOpener. */
    private AffineTransform3D referenceLLS7Transform(AffineTransform3D rootTransform) {
        AffineTransform3D latticeTransform = new AffineTransform3D();

        double angle = -60.0 / 180 * Math.PI;

        latticeTransform.set(
                1, 0, 0, 0,
                0, Math.cos(angle), 0, 0,
                0, +Math.sin(angle), -1, 0);

        AffineTransform3D rotateX = new AffineTransform3D();
        rotateX.rotate(0, Math.PI / 2.0);

        latticeTransform.preConcatenate(rotateX);
        AffineTransform3D addOffset = rootTransform.copy();

        double ox = addOffset.get(0, 3);
        double oy = addOffset.get(1, 3);
        double oz = addOffset.get(2, 3);

        addOffset.identity();
        addOffset.set(ox, 0, 3);
        addOffset.set(oy, 1, 3);
        addOffset.set(oz, 2, 3);

        AffineTransform3D rmOffset = addOffset.inverse();

        latticeTransform.preConcatenate(addOffset);
        latticeTransform.concatenate(rmOffset);

        rootTransform.preConcatenate(latticeTransform);
        return rootTransform;
    }
}
