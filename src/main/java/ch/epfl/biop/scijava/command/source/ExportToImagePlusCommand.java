package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.CZTRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
import ij.ImagePlus;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.*;
import java.util.List;
import java.util.function.Function;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Export Sources To ImageJ1 (ignore location)")
public class ExportToImagePlusCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Sources to export")
    public SourceAndConverter[] sacs;

    @Parameter(label = "Exported Image Name")
    public String name = "Image_00";

    @Parameter(label = "Resolution level (0 = highest)")
    public int level;

    @Parameter( label = "Select Range", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String range = "You can use commas or colons to separate ranges. eg. '1:10' or '1,3,5,8' ";

    @Parameter( label = "Selected Channels. Leave blank for all", required = false )
    String range_channels = "";

    @Parameter( label = "Selected Slices. Leave blank for all", required = false )
    String range_slices = "";

    @Parameter( label = "Selected Timepoints. Leave blank for all", required = false )
    String range_frames = "";

    @Parameter( label = "Export mode", choices = {"Normal", "Virtual", "Virtual no-cache"}, required = false )
    String export_mode = "Non virtual";

    @Parameter( label = "Monitor loaded data")
    Boolean monitor = false;

    @Parameter( label = "Acquire channels in parallel (Normal only)", required = false)
    Boolean parallelC = false;

    @Parameter( label = "Acquire slices in parallel (Normal only)", required = false)
    Boolean parallelZ = false;

    @Parameter( label = "Acquire timepoints in parallel (Normal only)", required = false)
    Boolean parallelT = false;

    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus imp_out;

    @Parameter( label = "Image Info", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String message = "[SX: , SY:, SZ:, #C:, #T:], ? Mb";

    @Parameter
    TaskService taskService;

    @Override
    public void run() {

        List<SourceAndConverter> sources = sorter.apply(Arrays.asList(sacs));

        int maxTimeFrames = SourceAndConverterHelper.getMaxTimepoint(sacs);

        int maxZSlices = (int) sacs[0].getSpimSource().getSource(0,level).dimension(2);

        CZTRange range;

        try {
            range = new CZTRange.Builder()
                    .setC(range_channels)
                    .setZ(range_slices)
                    .setT(range_frames)
                    .get(sacs.length, maxZSlices,maxTimeFrames);
        } catch (Exception e) {
            System.err.println("Invalid range "+e.getMessage());
            return;
        }

        Task task = null;

        if (monitor) task = taskService.createTask(name+" export");

        switch (export_mode) {
            case "Normal":
                imp_out = ImagePlusGetter
                        .getImagePlus(name, sources, level, range, parallelC, parallelZ, parallelT, task);
                break;
            case "Virtual":
                imp_out = ImagePlusGetter
                        .getVirtualImagePlus(name, sources, level, range, true, task);
                break;
            case "Virtual no-cache":
                imp_out = ImagePlusGetter
                        .getVirtualImagePlus(name, sources, level, range, false,  null);
                break;
            default: throw new UnsupportedOperationException("Unrecognized export mode "+export_mode);
        }
    }

    public Function<Collection<SourceAndConverter>,List<SourceAndConverter>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultNoGeneric(sacslist);

}
