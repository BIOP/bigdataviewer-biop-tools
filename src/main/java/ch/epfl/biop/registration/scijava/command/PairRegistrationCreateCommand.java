package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;

import java.util.List;

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Register>Create registration pair",
        description = "Creates a new registration pair from fixed and moving sources for the Warpy workflow")
public class PairRegistrationCreateCommand implements Command {

    @Parameter(label = "Fixed Source(s)",
            description = "The reference source(s) that will remain stationary during registration",
            style = "sorted")
    SourceAndConverter<?>[] fixed_sources;

    @Parameter(label = "Moving Source(s)",
            description = "The source(s) to be registered and aligned to the fixed source(s)",
            style = "sorted")
    SourceAndConverter<?>[] moving_sources;

    @Parameter(label = "Registration Name",
            description = "A unique name to identify this registration pair")
    String registration_name;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Registration Pair",
            description = "The created registration pair object")
    RegistrationPair registration_pair;

    @Parameter
    ObjectService objectService;

    @Override
    public void run() {
        List<RegistrationPair> allRegistrations = objectService.getObjects(RegistrationPair.class);

        if (allRegistrations.stream().anyMatch(rps -> rps.getName().equals(registration_name))) {
            System.err.println("A registration sequence named "+registration_name+" already exists! Please name it differently or delete the existing one.");
            return;
        }

        registration_pair = new RegistrationPair(fixed_sources,0,moving_sources,0, registration_name, true);
        objectService.addObject(registration_pair);

    }
}

