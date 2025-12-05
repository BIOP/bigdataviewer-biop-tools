package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.CZTRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings({"unused", "CanBeFinal"})
@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Export Sources To ImageJ1 (ignore location)")
public class ExportToImagePlusCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Sources to export")
    public SourceAndConverter<?>[] sacs;

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
    Boolean parallel_c = false;

    @Parameter( label = "Acquire slices in parallel (Normal only)", required = false)
    Boolean parallel_z = false;

    @Parameter( label = "Acquire timepoints in parallel (Normal only)", required = false)
    Boolean parallel_t = false;

    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus imp_out;

    @Parameter( label = "Image Info", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String message = "[SX: , SY:, SZ:, #C:, #T:], ? Mb";

    @Parameter
    TaskService taskService;

    @Override
    public void run() {

        List<SourceAndConverter<?>> sources = sorter.apply(Arrays.asList(sacs));

        int numFrames = SourceAndConverterHelper.getMaxTimepoint(sacs)+1;

        int maxZSlices = (int) sacs[0].getSpimSource().getSource(0,level).dimension(2);

        CZTRange range;

        try {
            range = new CZTRange.Builder()
                    .setC(range_channels)
                    .setZ(range_slices)
                    .setT(range_frames)
                    .get(sacs.length, maxZSlices, numFrames);

        } catch (Exception e) {
            System.err.println("Invalid range "+e.getMessage());
            return;
        }

        Task task = null;

        if (monitor) task = taskService.createTask(name+" export");

        imp_out = computeImage(sources, task, range, name, level, parallel_c, parallel_z, parallel_t, export_mode);

    }

    protected static <T extends NumericType<T> & NativeType<T>> ImagePlus computeImage(List<SourceAndConverter<?>> sources,
                                                                         Task task, CZTRange range,
                                                                         String name,
                                                                         int level,
                                                                         boolean parallelC,
                                                                         boolean parallelZ,
                                                                         boolean parallelT,
                                                                         String export_mode) throws UnsupportedOperationException {

        Object pixelTest = sources.get(0).getSpimSource().getType();

        if (!(pixelTest instanceof NumericType)) {
            throw new UnsupportedOperationException("A "+pixelTest.getClass()+" pixel is not numeric, can't export sources");
        } else if (!(pixelTest instanceof NativeType)) {
            throw new UnsupportedOperationException("A "+pixelTest.getClass()+" pixel is not native, can't export sources");
        }

        Class<?> clazz = sources.get(0).getSpimSource().getType().getClass();

        IJ.log("Exporting sources which is assumed to be of type "+clazz.getSimpleName());
        for (SourceAndConverter<?> source : sources) {
            if (!(clazz.isInstance(source.getSpimSource().getType()))) {
                IJ.log("Source "+source.getSpimSource().getName()+" is not a "+clazz.getSimpleName()+" source. Ignoring.");
            }
        }
        List<SourceAndConverter<T>> sanitizedList =
                sources.stream()
                        .filter(source -> clazz.isInstance(source.getSpimSource().getType()))
                        .map(source -> (SourceAndConverter<T>) source)
                        .collect(Collectors.toList());
        switch (export_mode) {
            case "Normal":
                return ImagePlusGetter
                        .getImagePlus(name, sanitizedList, level, range, parallelC, parallelZ, parallelT, task);
            case "Virtual":
                return ImagePlusGetter
                        .getVirtualImagePlus(name, sanitizedList, level, range, true, task);
            case "Virtual no-cache":
                return ImagePlusGetter
                        .getVirtualImagePlus(name, sanitizedList, level, range, false,  null);
            default: throw new UnsupportedOperationException("Unrecognized export mode "+export_mode);
        }
    }

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = SourceAndConverterHelper::sortDefaultGeneric;

}
