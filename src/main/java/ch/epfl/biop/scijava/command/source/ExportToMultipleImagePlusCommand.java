package ch.epfl.biop.scijava.command.source;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.bioformats.imageloader.FileIndex;
import ch.epfl.biop.bdv.bioformats.imageloader.SeriesNumber;
import ch.epfl.biop.scijava.command.bdv.OverviewerCommand;
import ch.epfl.biop.sourceandconverter.exporter.CZTRange;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusGetter;
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
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Export Sources To ImageJ1")
public class ExportToMultipleImagePlusCommand implements BdvPlaygroundActionCommand {

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

    @Parameter(label = "Split by dataset entities, comma separated (channel, fileseries)")
    String entitiesSplit = "";

    Map<String, Class<? extends Entity>> entityClasses = new HashMap<>();

    @Parameter(type = ItemIO.OUTPUT)
    public List<ImagePlus> imps_out = new ArrayList<>();

    @Override
    public void run() {

        entityClasses.put("TILE", Tile.class);
        entityClasses.put("ILLUMINATION", Illumination.class);
        entityClasses.put("ANGLE", Angle.class);
        entityClasses.put("FILE", FileIndex.class);
        entityClasses.put("SERIES", SeriesNumber.class);

        List<Class<? extends Entity>> entSplit = new ArrayList<>();

        for (String entity : entitiesSplit.split(",")) {
            String ent = entity.trim().toUpperCase();
            if (!entityClasses.containsKey(ent)){
                System.err.println("Unrecognized entity class "+ent);
            } else {
                System.out.println("Splitting by "+ent);
                entSplit.add(entityClasses.get(ent));
            }
        }

        //Map<SourceAndConverter, Integer> mapSacToMml = new HashMap<>();

        /*for (SourceAndConverter sac : sacs) {
            mapSacToMml.put(sac, level);
        }*/

        List<SourceAndConverter<?>> sourceList = sorter.apply(Arrays.asList(sacs));

        Map<OverviewerCommand.SacProperties, List<SourceAndConverter>> sacClasses = sourceList
                .stream()
                .collect(Collectors.groupingBy(sac -> {
                    OverviewerCommand.SacProperties props = new OverviewerCommand.SacProperties(sac);
                    for (Class<? extends Entity> entityClass : entSplit) {
                        props.splitByEntity(entityClass);
                    }
                    return props; //new SacProperties(sac)
                }));

        /*int timepointbegin = 0;
        // Sort according to location = affine transform 3d of sources

        List<AffineTransform3D> locations = sourceList.stream().map(sac -> {
            AffineTransform3D at3d = new AffineTransform3D();
            sac.getSpimSource().getSourceTransform(timepointbegin, level, at3d);
            return at3d;
        }).collect(Collectors.toList());

        Map<AffineTransform3D, List<SourceAndConverter>> sacSortedPerLocation = new HashMap<>();

        for (int iSource = 0; iSource < locations.size(); iSource++) {
            AffineTransform3D at3d  = locations.get(iSource);
            Optional<AffineTransform3D> tr = sacSortedPerLocation.keySet().stream()
                    .filter(tr_test -> MatrixApproxEquals(tr_test.getRowPackedCopy(), at3d.getRowPackedCopy())).findFirst();
            if (tr.isPresent()) {
                sacSortedPerLocation.get(tr.get()).add(sourceList.get(iSource));
            } else {
                List<SourceAndConverter> list = new ArrayList<>();
                list.add(sourceList.get(iSource));
                sacSortedPerLocation.put(at3d, list);
            }
        }

        List<SourceAndConverter> sacs0nonSorted = new ArrayList<>();

        sacSortedPerLocation.values().stream().map(l -> l.get(0)).forEach(sac -> sacs0nonSorted.add(sac));

        List<SourceAndConverter> sacs0Sorted = sorter.apply(sacs0nonSorted);

        Map<Integer, AffineTransform3D> indexToLocation = new HashMap<>();

        sacSortedPerLocation.keySet().stream().forEach(location -> {
            SourceAndConverter sac0 = sacSortedPerLocation.get(location).get(0);
            indexToLocation.put(sacs0Sorted.indexOf(sac0), location);
        });

        Stream<Integer> locationsIndexes = indexToLocation.keySet().stream().sorted();

        if (parallel) locationsIndexes = locationsIndexes.parallel();*/

        /*List<SourceAndConverter<?>> sourceList = sorter.apply(Arrays.asList(sacs));

        Map<OverviewerCommand.SacProperties, List<SourceAndConverter>> sacClasses = sourceList
                .stream()
                .collect(Collectors.groupingBy(sac -> {
                    OverviewerCommand.SacProperties props = new OverviewerCommand.SacProperties(sac);
                    for (Class<? extends Entity> entityClass : entSplit) {
                        props.splitByEntity(entityClass);
                    }
                    return props; //new SacProperties(sac)
                }));*/

        Map<SourceAndConverter<?>, List<OverviewerCommand.SacProperties>> keySetSac = sacClasses.keySet().stream().collect(Collectors.groupingBy(p -> p.getSource()));

        List<SourceAndConverter<?>> sortedSacs = sorter.apply(keySetSac.keySet());

        List<SourceAndConverter<?>> sacsToDisplay = new ArrayList<>();

        int timepointbegin = 0;

        sortedSacs.forEach(sacKey -> { // .parallel()

            AffineTransform3D at3d = new AffineTransform3D();
            sacKey.getSpimSource().getSourceTransform(timepointbegin, level, at3d);
            AffineTransform3D location = at3d; //indexToLocation.get(idx);

            OverviewerCommand.SacProperties sacPropsKey = keySetSac.get(sacKey).get(0);
            List<SourceAndConverter> sources = sacClasses.get(sacPropsKey);// sacSortedPerLocation.get(location);
            //List<SourceAndConverter> sources = sacSortedPerLocation.get(location).stream().map(sac -> (SourceAndConverter) sac).collect(Collectors.toList());
            ImagePlus imp_out;
            String name = sacKey.getSpimSource().getName();


            int maxTimeFrames = SourceAndConverterHelper.getMaxTimepoint(sources.toArray(new SourceAndConverter[0]));

            int maxZSlices = (int) sources.get(0).getSpimSource().getSource(0,level).dimension(2);

            CZTRange range;

            try {

                range = new CZTRange.Builder()
                        .setC(range_channels)
                        .setZ(range_slices)
                        .setT(range_frames)
                        .get(sources.size(), maxZSlices, maxTimeFrames);

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

                imps_out.add(imp_out);
                //imp_out.show();
            } catch (Exception e) {
                System.err.println("Invalid range "+e.getMessage());
            }


        });
        //imps_out.forEach(ImagePlus::show);
        sacs = null; // free mem ?
    }

    //public Function<Collection<SourceAndConverter>,List<SourceAndConverter>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultNoGeneric(sacslist);

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultGeneric(sacslist);

}
