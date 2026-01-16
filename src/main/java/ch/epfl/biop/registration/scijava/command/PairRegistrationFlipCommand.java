package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.sourceandconverter.affine.AffineRegistration;
import ch.epfl.biop.sourceandconverter.processor.SourcesIdentity;
import ch.epfl.biop.sourceandconverter.processor.SourcesProcessor;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair - Flip",
        description = "Applies a flip transformation to the moving sources along X, Y, or Z axis")
public class PairRegistrationFlipCommand extends AbstractPairRegistration2DCommand implements Command {

    @Parameter(label = "Axis",
            description = "Axis along which to flip the moving sources",
            choices = {"X", "Y", "Z"})
    String axis = "X";

    @Parameter(label = "Keep Center",
            description = "When checked, maintains the center position of the moving sources unchanged")
    boolean keep_center = true;

    @Override
    protected void addRegistrationParameters(Map<String, Object> parameters) {
        AffineTransform3D at3D = new AffineTransform3D();
        at3D.identity();

        // Apply flip transformation
        switch (axis) {
            case "X":
                at3D.set(-1, 0, 0);
                break;
            case "Y":
                at3D.set(-1, 1, 1);
                break;
            case "Z":
                at3D.set(-1, 2, 2);
                break;
        }

        if (keep_center) {
            // Maintain center of moving sources constant
            SourceAndConverter<?> movingSac = registration_pair.getMovingSourcesRegistered()[0];
            int timepoint = registration_pair.getMovingTimepoint();

            AffineTransform3D sourceTransform = new AffineTransform3D();
            movingSac.getSpimSource().getSourceTransform(timepoint, 0, sourceTransform);

            long[] dims = new long[3];
            movingSac.getSpimSource().getSource(timepoint, 0).dimensions(dims);

            RealPoint ptCenterPixel = new RealPoint(
                (dims[0] - 1.0) / 2.0,
                (dims[1] - 1.0) / 2.0,
                (dims[2] - 1.0) / 2.0
            );

            RealPoint ptCenterGlobalBefore = new RealPoint(3);
            sourceTransform.apply(ptCenterPixel, ptCenterGlobalBefore);

            RealPoint ptCenterGlobalAfter = new RealPoint(3);
            at3D.apply(ptCenterGlobalBefore, ptCenterGlobalAfter);

            // Adjust translation to maintain center
            double[] m = at3D.getRowPackedCopy();
            m[3] -= ptCenterGlobalAfter.getDoublePosition(0) - ptCenterGlobalBefore.getDoublePosition(0);
            m[7] -= ptCenterGlobalAfter.getDoublePosition(1) - ptCenterGlobalBefore.getDoublePosition(1);
            m[11] -= ptCenterGlobalAfter.getDoublePosition(2) - ptCenterGlobalBefore.getDoublePosition(2);
            at3D.set(m);
        }

        parameters.put(AffineRegistration.TRANSFORM_KEY, AffineRegistration.affineTransform3DToString(at3D));
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        AffineRegistration reg = new AffineRegistration();
        reg.setRegistrationName("Flip " + axis);
        return reg;
    }

    @Override
    protected boolean validate() {
        return true;
    }

    @Override
    protected SourcesProcessor getSourcesProcessorFixed() {
        return new SourcesIdentity();
    }

    @Override
    protected SourcesProcessor getSourcesProcessorMoving() {
        return new SourcesIdentity();
    }
}
