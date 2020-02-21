package ch.epfl.biop.scijava.command;

import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "BigDataViewer>Sources>Export>As ImagePlus")
public class ExportToImagePlusCommand implements Command {

    @Parameter
    SourceAndConverter sac;

    @Parameter
    int level;

    @Parameter
    int timepoint;

    @Parameter(type = ItemIO.OUTPUT)
    ImagePlus imp_out;

    @Override
    public void run() {
        imp_out = ImageJFunctions.wrap(sac.getSpimSource().getSource(timepoint,level), sac.getSpimSource().getName());
    }
}
