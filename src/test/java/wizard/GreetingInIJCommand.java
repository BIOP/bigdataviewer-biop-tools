package wizard;

import ij.IJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;

@Plugin(type = BdvPlaygroundActionCommand.class)
public class GreetingInIJCommand implements BdvPlaygroundActionCommand {

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
