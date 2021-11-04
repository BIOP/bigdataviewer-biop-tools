package wizard;

import ij.IJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class)
public class GreetingInIJCommand implements Command {

    @Parameter
    String name;

    @Parameter(choices = {"familiar", "formal"})
    String type;

    @Override
    public void run() {
        switch (type) {
            case "familiar":
                IJ.log("Hi "+name);
                break;
            case "formal":
                IJ.log("Hello "+name);
                break;
            default:
                IJ.log("You broke the matrix");
                break;
        }
    }
}
