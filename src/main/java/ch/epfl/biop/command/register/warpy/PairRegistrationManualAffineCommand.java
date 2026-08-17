package ch.epfl.biop.command.register.warpy;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.source.affine.ManualAffineRegistration;
import ch.epfl.biop.source.processor.SourcesIdentity;
import ch.epfl.biop.source.processor.SourcesProcessor;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

import java.util.Map;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.RegisterMenu, weight = BdvPgMenus.RegisterW),
                @Menu(label = "Warpy", weight = -2),
                @Menu(label = "Register Pair - Manual Affine", weight = 5.5)
        },
        description = "Opens a viewer where the moving sources are manually dragged, rotated and scaled onto the fixed sources")
public class PairRegistrationManualAffineCommand extends AbstractPairRegistration2DCommand implements BdvPlaygroundActionCommand {

    @Override
    protected void addRegistrationParameters(Map<String, Object> parameters) {
        // Nothing required: the transformation is entirely defined by the user interaction
    }

    @Override
    Registration<SourceAndConverter<?>[]> getRegistration() {
        return new ManualAffineRegistration();
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
