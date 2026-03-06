package ch.epfl.biop.command.exporter;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.opener.OpenerHelper;
import ch.epfl.biop.command.display.bdv.SourcesOverviewCommand;
import ch.epfl.biop.source.exporter.CZTRange;
import ij.ImagePlus;
import mpicbg.spim.data.generic.base.Entity;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.source.SourceHelper;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Plugin(type = BdvPlaygroundActionCommand.class,
        //menuPath = BdvPgMenus.RootMenu+"Export>Source - Export To ImagePlus",
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = BdvPgMenus.ExportMenu, weight = BdvPgMenus.ExportW),
                @Menu(label = "Source - Export To ImagePlus", weight = 6)
        },
        description = "Exports sources to multiple ImagePlus files, respecting their spatial locations")
public class SourcesToMultipleImagePlusExportCommand implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(SourcesToMultipleImagePlusExportCommand.class);

    @Parameter(label = "Select Source(s)",
            description = "The sources to export")
    public SourceAndConverter[] sources;

    @Parameter(label = "Resolution Level",
            description = "Pyramid level to export (0 = highest resolution)")
    public int level;

    @Parameter(label = "Select Range",
            visibility = ItemVisibility.MESSAGE,
            persist = false,
            required = false)
    String range = "You can use commas or colons to separate ranges. eg. '1:10' or '1,3,5,8' ";

    @Parameter(label = "Selected Channels",
            description = "Channel indices to export (e.g., '0:2' or '0,1'). Leave blank for all",
            required = false)
    String range_channels = "";

    @Parameter(label = "Selected Slices",
            description = "Z-slice indices to export (e.g., '0:100'). Leave blank for all",
            required = false)
    String range_slices = "";

    @Parameter(label = "Selected Timepoints",
            description = "Timepoint indices to export (e.g., '0:10'). Leave blank for all",
            required = false)
    String range_frames = "";

    @Parameter(label = "Export Mode",
            description = "Normal loads all data; Virtual creates a lazy-loading stack",
            choices = {"Normal", "Virtual", "Virtual no-cache"},
            required = false)
    String export_mode = "Non virtual";

    @Parameter(label = "Monitor Progress",
            description = "When checked, displays a progress indicator during export")
    Boolean monitor = false;

    @Parameter(label = "Export in Parallel",
            description = "When checked, exports multiple images simultaneously")
    Boolean parallel = false;

    @Parameter(label = "Parallel Channels",
            description = "When checked, acquires channels in parallel (Normal mode only)",
            required = false)
    Boolean parallel_c = false;

    @Parameter(label = "Parallel Slices",
            description = "When checked, acquires Z-slices in parallel (Normal mode only)",
            required = false)
    Boolean parallel_z = false;

    @Parameter(label = "Parallel Timepoints",
            description = "When checked, acquires timepoints in parallel (Normal mode only)",
            required = false)
    Boolean parallel_t = false;

    @Parameter(label = "Split by Entities",
            description = "Comma-separated entity types to split by (e.g., 'channel, imagename')")
    String entities_split = "";

    Map<String, Class<? extends Entity>> entityClasses = OpenerHelper.getEntities();;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Exported Images",
            description = "The exported ImagePlus images")
    public List<ImagePlus> imps_out = new ArrayList<>();

    @Parameter(required = false)
    public boolean verbose = false;

    @Parameter
    TaskService taskService;

    @Override
    public void run() {

        List<Class<? extends Entity>> entSplit = new ArrayList<>();

        for (String entity : entities_split.split(",")) {
            String ent = entity.trim().toUpperCase();
            if ((!entityClasses.containsKey(ent))&&(!ent.trim().equals(""))){
                System.err.println("Unrecognized entity class "+ent);
            } else {
                System.out.println("Splitting by "+ent);
                entSplit.add(entityClasses.get(ent));
            }
        }

        List<SourceAndConverter<?>> sourceList = sorter.apply(Arrays.asList(sources));

        Map<SourcesOverviewCommand.SacProperties, List<SourceAndConverter<?>>> sourceClasses = sourceList
                .stream()
                .collect(Collectors.groupingBy(source -> {
                    SourcesOverviewCommand.SacProperties props = new SourcesOverviewCommand.SacProperties(source);
                    for (Class<? extends Entity> entityClass : entSplit) {
                        props.splitByEntity(entityClass);
                    }
                    return props;
                }));

        Map<SourceAndConverter<?>, List<SourcesOverviewCommand.SacProperties>> keySetSac = sourceClasses.keySet().stream().collect(Collectors.groupingBy(p -> p.getSource()));

        List<SourceAndConverter<?>> sortedSacs = sorter.apply(keySetSac.keySet());
        Stream<SourceAndConverter<?>> sortedSacsStream;
        if (!parallel) {
            sortedSacsStream = sortedSacs.stream();
        } else {
            sortedSacsStream = sortedSacs.parallelStream();
        }

        ImagePlus[] temporaryImageArray = new ImagePlus[sortedSacs.size()];

        int timepointbegin = 0;
        int nImages = sortedSacs.size();

        AtomicInteger iImage = new AtomicInteger();

        sortedSacsStream.forEach(sourceKey -> {

            Task task = null;
            String name = sourceKey.getSpimSource().getName();

            if (monitor) {
                task = taskService.createTask(name+" export");
                task.setStatusMessage("Reading first plane of ("+iImage.incrementAndGet()+"/"+nImages+") - "+sourceKey.getSpimSource().getName());
            }

            AffineTransform3D at3d = new AffineTransform3D();
            sourceKey.getSpimSource().getSourceTransform(timepointbegin, level, at3d);

            SourcesOverviewCommand.SacProperties sourcePropsKey = keySetSac.get(sourceKey).get(0);
            List<SourceAndConverter<?>> sources = sourceClasses.get(sourcePropsKey);

            ImagePlus imp_out;
            int numFrames = SourceHelper.getMaxTimepoint(sources.toArray(new SourceAndConverter[0]))+1;

            int maxZSlices = (int) sources.get(0).getSpimSource().getSource(0,level).dimension(2);

            CZTRange range;

            try {

                range = new CZTRange.Builder()
                        .setC(range_channels)
                        .setZ(range_slices)
                        .setT(range_frames)
                        .get(sources.size(), maxZSlices, numFrames);

                temporaryImageArray[sortedSacs.indexOf(sourceKey)] =
                        SourcesToImagePlusExportCommand.computeImage(sources, task, range, name, level, parallel_c, parallel_z, parallel_t, export_mode);

            } catch (Exception e) {
                logger.error("Invalid range "+e.getMessage());
            }
        });

        imps_out.addAll(Arrays.asList(temporaryImageArray));

        sources = null; // free mem ?
    }

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = SourceHelper::sortDefaultGeneric;

}
