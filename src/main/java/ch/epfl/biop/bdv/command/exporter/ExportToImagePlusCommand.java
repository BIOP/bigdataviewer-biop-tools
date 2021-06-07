package ch.epfl.biop.bdv.command.exporter;

import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import spimdata.imageplus.ImagePlusHelper;

import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Show Sources (IJ1)")
public class ExportToImagePlusCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Sources to export")
    public SourceAndConverter[] sacs;

    @Parameter(label = "Resolution level (0 = highest)")
    public int level;

    @Parameter(label = "Start Timepoint (starts at 0)")
    public int timepointbegin = 0;

    @Parameter(label = "Number of timepoints", min = "1")
    public int numtimepoints = 1;

    @Parameter(label = "Time step", min = "1")
    public int timestep = 1;

    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus imp_out;

    @Override
    public void run() {

        Map<SourceAndConverter, Integer> mapSacToMml = new HashMap<>();

        for (SourceAndConverter sac : sacs) {
            mapSacToMml.put(sac, level);
        }

        List<SourceAndConverter<?>> sourceList = sorter.apply(Arrays.asList(sacs));
        imp_out = ImagePlusHelper.wrap(sourceList.stream().map(sac -> (SourceAndConverter) sac).collect(Collectors.toList()), mapSacToMml, timepointbegin, numtimepoints, timestep);
        AffineTransform3D at3D = new AffineTransform3D();
        sacs[0].getSpimSource().getSourceTransform(timepointbegin, level, at3D);
        String unit = "px";
        if (sacs[0].getSpimSource().getVoxelDimensions() != null) {
            unit = sacs[0].getSpimSource().getVoxelDimensions().unit();
            if (unit==null) {
                unit = "px";
            }
        }
        imp_out.setTitle(sourceList.get(0).getSpimSource().getName());
        ImagePlusHelper.storeExtendedCalibrationToImagePlus(imp_out,at3D,unit, timepointbegin);
        imp_out.show();
    }

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultGeneric(sacslist);

}
