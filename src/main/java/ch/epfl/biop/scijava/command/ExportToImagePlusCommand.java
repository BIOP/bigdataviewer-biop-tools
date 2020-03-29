package ch.epfl.biop.scijava.command;

import bdv.util.ImagePlusHelper;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import ij.process.LUT;
import net.imglib2.display.ColorConverter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.awt.*;

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

        AffineTransform3D at3D = new AffineTransform3D();
        sac.getSpimSource().getSourceTransform(timepoint, level, at3D);

        ImagePlusHelper.storeMatrixToImagePlus(imp_out, at3D);

        // Color and Brightness contrast

        if (sac.getConverter() instanceof ColorConverter) {
            imp_out.setDisplayRange(((ColorConverter) sac.getConverter()).getMin(), ((ColorConverter) sac.getConverter()).getMax());
            ARGBType c = ((ColorConverter) sac.getConverter()).getColor();
            imp_out.setLut(LUT.createLutFromColor(new Color(ARGBType.red(c.get()), ARGBType.green(c.get()), ARGBType.blue(c.get()))));
        }

    }



}
