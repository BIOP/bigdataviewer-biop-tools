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
        imp_out.setDimensions(1,(int)sac.getSpimSource().getSource(timepoint,level).dimension(2),1);
        /*Thread monitor = new Thread(() -> {
            while (true) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // Total amount of free memory available to the JVM
                System.out.println("Free memory (Mb): " +
                        (int) (Runtime.getRuntime().freeMemory()/(1024*1024)));

                // Total memory currently in use by the JVM
                System.out.println("Total memory (Mb): " +
                        (int) (Runtime.getRuntime().totalMemory()/(1024*1024)));

            }
        });
        monitor.start();*/
    }
}
