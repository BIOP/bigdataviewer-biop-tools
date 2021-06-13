package ch.epfl.biop.bdv.command.exporter;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.operetta.utils.HyperRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
import ij.ImagePlus;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import spimdata.imageplus.ImagePlusHelper;

import java.util.*;
import java.util.List;
import java.util.function.Function;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Show Sources (IJ1)")
public class ExportToImagePlusCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Sources to export")
    public SourceAndConverter[] sacs;

    @Parameter(label = "Exported Image Name")
    public String name = "Image_00";

    @Parameter(label = "Resolution level (0 = highest)")
    public int level;

    @Parameter( label = "Select Range", callback = "updateMessage", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String range = "You can use commas or colons to separate ranges. eg. '1:10' or '1,3,5,8' ";

    //@Parameter( label = "Selected Slices. Leave blank for all", required = false )
    //private String selected_slices_str = "";

    @Parameter( label = "Selected Timepoints. Leave blank for all", required = false )
    private String selected_timepoints_str = "";

    @Parameter( label = "Export mode", choices = {"Normal", "Virtual", "Virtual no-cache"}, required = false )
    private String export_mode = "Non virtual";

    @Parameter( label = "Monitor export speed")
    private Boolean monitor = false;

    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus imp_out;

    @Override
    public void run() {

        //Map<SourceAndConverter, Integer> mapSacToMml = new HashMap<>();

        //List<SourceAndConverter> sources = Arrays.asList(sacs);

        List<SourceAndConverter> sources = sorter.apply(Arrays.asList(sacs));

        HyperRange.Builder rangeBuilder = ImagePlusGetter.fromSources(sources, 0, level);

        if ((selected_timepoints_str!=null)&&(selected_timepoints_str.trim()!="")) {
            rangeBuilder = rangeBuilder.setRangeT(selected_timepoints_str);
        }

        /*if ((selected_slices_str!=null)&&(selected_slices_str.trim()!="")) {
            if (export_mode.equals("Normal")) {
                System.err.println("SubSlices Selection unsupported in non virtual mode!");
            } else {
                rangeBuilder = rangeBuilder.setRangeZ(selected_slices_str);
            }
        }*/

        HyperRange range = rangeBuilder.build();

        //imp_out = ImagePlusHelper.wrap(sourceList.stream().map(sac -> (SourceAndConverter) sac).collect(Collectors.toList()), mapSacToMml, timepointbegin, numtimepoints, timestep);

        switch (export_mode) {
            case "Normal":
                imp_out = ImagePlusGetter.getImagePlus(name, sources, level, range, monitor);
                break;
            case "Virtual":
                imp_out = ImagePlusGetter.getVirtualImagePlus(name, sources, level, range, true, monitor);
                break;
            case "Virtual no-cache":
                imp_out = ImagePlusGetter.getVirtualImagePlus(name, sources, level, range, false, false);
                break;
            default: throw new UnsupportedOperationException("Unrecognized export mode "+export_mode);
        }

        AffineTransform3D at3D = new AffineTransform3D();
        int timepointbegin = range.getRangeT().get(0);
        sacs[0].getSpimSource().getSourceTransform(range.getRangeT().get(0), level, at3D);
        String unit = "px";
        if (sacs[0].getSpimSource().getVoxelDimensions() != null) {
            unit = sacs[0].getSpimSource().getVoxelDimensions().unit();
            if (unit==null) {
                unit = "px";
            }
        }
        //imp_out.setTitle(sourceList.get(0).getSpimSource().getName());
        ImagePlusHelper.storeExtendedCalibrationToImagePlus(imp_out,at3D,unit, timepointbegin);
        imp_out.show();
    }

    public Function<Collection<SourceAndConverter>,List<SourceAndConverter>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultNoGeneric(sacslist);

}
