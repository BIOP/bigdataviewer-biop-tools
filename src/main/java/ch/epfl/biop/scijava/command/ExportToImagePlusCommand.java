package ch.epfl.biop.scijava.command;

import bdv.tools.brightness.ConverterSetup;
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
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Plugin(type = Command.class, menuPath = "BigDataViewer>Sources>Export>As ImagePlus")
public class ExportToImagePlusCommand implements Command {

    @Parameter
    public SourceAndConverter[] sacs;

    @Parameter
    public int level;

    @Parameter
    public int timepointBegin;

    @Parameter
    public int timepointEnd;

    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus imp_out;

    List<SourceAndConverter<?>> sourceList;

    @Override
    public void run() {

        // Sanity checks
        // 1. Timepoints : at least one timepoint
        if (timepointEnd<=timepointBegin) {
            timepointEnd = timepointBegin+1;
        }

        Map<SourceAndConverter, ConverterSetup> mapSacToCs = new HashMap<>();
        Map<SourceAndConverter, Integer> mapSacToMml = new HashMap<>();

        for (SourceAndConverter sac : sacs) {
            mapSacToCs.put(sac,SourceAndConverterServices.getSourceAndConverterDisplayService().getConverterSetup(sac));
            mapSacToMml.put(sac, level);
        }

        sourceList = sorter.apply(Arrays.asList(sacs));

        imp_out = ImagePlusHelper.wrap(sourceList.stream().map(sac -> (SourceAndConverter) sac).collect(Collectors.toList()), mapSacToCs, mapSacToMml, timepointBegin, timepointEnd, false );

        /*ImageJFunctions.wrap(sac.getSpimSource().getSource(timepoint,level), sac.getSpimSource().getName());
        imp_out.setDimensions(1,(int)sac.getSpimSource().getSource(timepoint,level).dimension(2),1);*/

        AffineTransform3D at3D = new AffineTransform3D();
        sacs[0].getSpimSource().getSourceTransform(timepointBegin, level, at3D);

        String unit = "px";

        if (sacs[0].getSpimSource().getVoxelDimensions() != null) {
            unit = sacs[0].getSpimSource().getVoxelDimensions().unit();
            if (unit==null) {
                unit = "px";
            }
        }

        ImagePlusHelper.storeExtendedCalibrationToImagePlus(imp_out,at3D,unit,timepointBegin);

    }


    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacs1ist -> BdvViewToImagePlusExportCommand.sortDefault(sacs1ist);

}
