package wizard;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class)
public class DefineNameCommand implements BdvPlaygroundActionCommand {

    @Parameter
    public String name;

    @Parameter(type = ItemIO.OUTPUT)
    public String name_output;

    @Override
    public void run() {
        name_output = name;
    }
}
