package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.sourceandconverter.affine.AffineRegistration;
import ch.epfl.biop.sourceandconverter.processor.SourcesIdentity;
import ch.epfl.biop.sourceandconverter.processor.SourcesProcessor;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair - Center moving sources on fixed sources",
        description = "Registration that centers moving sources over fixed sources."  )
public class PairRegistrationCenterCommand extends AbstractPairRegistration2DCommand implements Command {

    @Override
    protected void addRegistrationParameters(Map<String, Object> parameters) {
        AffineTransform3D affineTransform3D = new AffineTransform3D();
        RealPoint centerFixed = SourceAndConverterHelper.getSourceAndConverterCenterPoint(registration_pair.getFixedSources()[0], registration_pair.getFixedTimepoint());
        RealPoint centerMoving = SourceAndConverterHelper.getSourceAndConverterCenterPoint(registration_pair.getMovingSourcesRegistered()[0], registration_pair.getMovingTimepoint());
        double dx = centerFixed.getDoublePosition(0)-centerMoving.getDoublePosition(0);
        double dy = centerFixed.getDoublePosition(1)-centerMoving.getDoublePosition(1);
        double dz = centerFixed.getDoublePosition(2)-centerMoving.getDoublePosition(2);
        affineTransform3D.translate(dx,dy,dz);
        parameters.put(AffineRegistration.TRANSFORM_KEY, AffineRegistration.affineTransform3DToString(affineTransform3D));
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new AffineRegistration();
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
