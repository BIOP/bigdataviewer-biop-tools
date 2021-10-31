package wizard;

import ij.IJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class)
public class GreetingInIJCommand implements Command {

    @Parameter
    String name;

    @Override
    public void run() {
        IJ.log("Hello "+name);
    }
}
