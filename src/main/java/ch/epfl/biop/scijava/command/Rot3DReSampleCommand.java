package ch.epfl.biop.scijava.command;

import ij.ImagePlus;
import ij.plugin.frame.RoiManager;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "3DRot>Rotation 3D Transform")
public class Rot3DReSampleCommand implements Command {

    @Parameter
    ImagePlus imp_in;

    @Parameter
    ImagePlus imp_out;

    @Parameter
    RoiManager rm;

    public void run() {

    }
}
