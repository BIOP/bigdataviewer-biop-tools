package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.legacy.bioformats.entity.FileIndex;
import ch.epfl.biop.bdv.img.legacy.bioformats.entity.SeriesNumber;
import ch.epfl.biop.scijava.command.bdv.OverviewerCommand;
import ch.epfl.biop.sourceandconverter.exporter.CZTRange;
import ij.ImagePlus;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Export Sources To ImageJ1")
public class ExportToMultipleImagePlusCommand implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(ExportToMultipleImagePlusCommand.class);

    @Parameter(label = "Sources to export")
    public SourceAndConverter[] sacs;

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
    private String export_mode = "Non virtual";

    @Parameter( label = "Monitor loaded data")
    private Boolean monitor = false;

    @Parameter( label = "Open images in parallel")
    private Boolean parallel = false;

    @Parameter( label = "Acquire channels in parallel (Normal only)", required = false)
    Boolean parallel_c = false;

    @Parameter( label = "Acquire slices in parallel (Normal only)", required = false)
    Boolean parallel_z = false;

    @Parameter( label = "Acquire timepoints in parallel (Normal only)", required = false)
    Boolean parallel_t = false;

    @Parameter(label = "Split by dataset entities, comma separated (channel, fileseries)")
    String entities_split = "";

    Map<String, Class<? extends Entity>> entityClasses = new HashMap<>();

    @Parameter(type = ItemIO.OUTPUT)
    public List<ImagePlus> imps_out = new ArrayList<>();

    @Parameter(required = false)
    public boolean verbose = false;

    @Parameter
    TaskService taskService;

    @Override
    public void run() {

        entityClasses.put("TILE", Tile.class);
        entityClasses.put("ILLUMINATION", Illumination.class);
        entityClasses.put("ANGLE", Angle.class);
        entityClasses.put("FILE", FileIndex.class);
        entityClasses.put("SERIES", SeriesNumber.class);

        List<Class<? extends Entity>> entSplit = new ArrayList<>();

        for (String entity : entities_split.split(",")) {
            String ent = entity.trim().toUpperCase();
            if ((!entityClasses.containsKey(ent))&&(ent!=null)&&(ent.trim()!="")){
                System.err.println("Unrecognized entity class "+ent);
            } else {
                System.out.println("Splitting by "+ent);
                entSplit.add(entityClasses.get(ent));
            }
        }

        List<SourceAndConverter<?>> sourceList = sorter.apply(Arrays.asList(sacs));

        Map<OverviewerCommand.SacProperties, List<SourceAndConverter<?>>> sacClasses = sourceList
                .stream()
                .collect(Collectors.groupingBy(sac -> {
                    OverviewerCommand.SacProperties props = new OverviewerCommand.SacProperties(sac);
                    for (Class<? extends Entity> entityClass : entSplit) {
                        props.splitByEntity(entityClass);
                    }
                    return props;
                }));

        Map<SourceAndConverter<?>, List<OverviewerCommand.SacProperties>> keySetSac = sacClasses.keySet().stream().collect(Collectors.groupingBy(p -> p.getSource()));

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

        sortedSacsStream.forEach(sacKey -> {

            Task task = null;
            String name = sacKey.getSpimSource().getName();

            if (monitor) {
                task = taskService.createTask(name+" export");
                task.setStatusMessage("Reading first plane of ("+iImage.incrementAndGet()+"/"+nImages+") - "+sacKey.getSpimSource().getName());
            }

            AffineTransform3D at3d = new AffineTransform3D();
            sacKey.getSpimSource().getSourceTransform(timepointbegin, level, at3d);

            OverviewerCommand.SacProperties sacPropsKey = keySetSac.get(sacKey).get(0);
            List<SourceAndConverter<?>> sources = sacClasses.get(sacPropsKey);

            ImagePlus imp_out;
            int maxTimeFrames = SourceAndConverterHelper.getMaxTimepoint(sources.toArray(new SourceAndConverter[0]));

            int maxZSlices = (int) sources.get(0).getSpimSource().getSource(0,level).dimension(2);

            CZTRange range;

            try {

                range = new CZTRange.Builder()
                        .setC(range_channels)
                        .setZ(range_slices)
                        .setT(range_frames)
                        .get(sources.size(), maxZSlices, maxTimeFrames);

                temporaryImageArray[sortedSacs.indexOf(sacKey)] =
                        ExportToImagePlusCommand.computeImage(sources, task, range, name, level, parallel_c, parallel_z, parallel_t, export_mode);

            } catch (Exception e) {
                logger.error("Invalid range "+e.getMessage());
            }
        });

        for(ImagePlus imp: temporaryImageArray) {
            imps_out.add(imp);
        }

        sacs = null; // free mem ?
    }

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultGeneric(sacslist);

}
