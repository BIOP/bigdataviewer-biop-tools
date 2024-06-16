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
        description = "Defines a registration pair"  )
public class PairRegistrationCreateCommand implements Command {

    @Parameter(label = "Fixed source for registration", description = "fixed sources", style = "sorted")
    SourceAndConverter<?>[] fixed_sources;

    @Parameter(label = "Moving source for registration", description = "moving sources", style = "sorted")
    SourceAndConverter<?>[] moving_sources;

    @Parameter
    String registration_name;

    @Parameter(type = ItemIO.OUTPUT)
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
