package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.sourceandconverter.bigwarp.SacBigWarp2DRegistration;
import ch.epfl.biop.sourceandconverter.processor.SourcesIdentity;
import ch.epfl.biop.sourceandconverter.processor.SourcesProcessor;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Register Pair 2D - BigWarp Spline",
        description = "Opens BigWarp for interactive manual landmark-based spline registration")

public class PairRegistrationBigWarp2DSplineCommand extends AbstractPairRegistration2DCommand implements Command {

    @Override
    protected void addRegistrationParameters(Map<String, Object> parameters) {
        // Nothing required
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new SacBigWarp2DRegistration();
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
