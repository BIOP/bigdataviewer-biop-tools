package ch.epfl.biop.command.register.warpy;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.source.bigwarp.BigWarpSource2DRegistration;
import ch.epfl.biop.source.processor.SourcesIdentity;
import ch.epfl.biop.source.processor.SourcesProcessor;
import org.scijava.command.Command;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Register>Warpy>Register Pair - BigWarp Spline 2D",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Register Pair - Spline BigWarp 2D", weight = 9.5)
        },
        description = "Opens BigWarp for interactive manual landmark-based spline registration")

public class PairRegistrationBigWarp2DSplineCommand extends AbstractPairRegistration2DCommand implements Command {

    @Override
    protected void addRegistrationParameters(Map<String, Object> parameters) {
        // Nothing required
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new BigWarpSource2DRegistration();
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
