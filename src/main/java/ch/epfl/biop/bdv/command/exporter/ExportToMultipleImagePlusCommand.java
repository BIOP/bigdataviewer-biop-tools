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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static sc.fiji.bdvpg.bdv.navigate.ViewerTransformSyncStopper.MatrixApproxEquals;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Show Sources (split) (IJ1)")
public class ExportToMultipleImagePlusCommand implements BdvPlaygroundActionCommand {

    @Parameter(label = "Sources to export")
    public SourceAndConverter[] sacs;

    @Parameter(label = "Resolution level (0 = highest)")
    public int level;

    @Parameter( label = "Select Range", callback = "updateMessage", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String range = "You can use commas or colons to separate ranges. eg. '1:10' or '1,3,5,8' ";

    @Parameter( label = "Selected Slices. Leave blank for all", required = false )
    private String selected_slices_str = "";

    @Parameter( label = "Selected Timepoints. Leave blank for all", required = false )
    private String selected_timepoints_str = "";

    @Parameter( label = "Export mode", choices = {"Normal", "Virtual", "Virtual no-cache"}, required = false )
    private String export_mode = "Non virtual";

    @Parameter( label = "Monitor loaded data")
    private Boolean monitor = false;

    @Parameter( label = "Open images in parallel")
    private Boolean parallel = false;

    //@Parameter(type = ItemIO.OUTPUT)
    //public ImagePlus imp_out;

    @Parameter(type = ItemIO.OUTPUT)
    public List<ImagePlus> imps_out = new ArrayList<>();

    HyperRange.Builder rangeBuilder;

    @Override
    public void run() {

        //Map<SourceAndConverter, Integer> mapSacToMml = new HashMap<>();

        /*for (SourceAndConverter sac : sacs) {
            mapSacToMml.put(sac, level);
        }*/

        List<SourceAndConverter> sourceList = sorter.apply(Arrays.asList(sacs));

        rangeBuilder = ImagePlusGetter.fromSources(sourceList, 0, level);

        if ((selected_timepoints_str!=null)&&(selected_timepoints_str.trim()!="")) {
            rangeBuilder = rangeBuilder.setRangeT(selected_timepoints_str);
        }

        if ((selected_slices_str!=null)&&(selected_slices_str.trim()!="")) {
            if (export_mode.equals("Normal")) {
                System.err.println("SubSlices Selection unsupported in non virtual mode!");
            } else {
                rangeBuilder = rangeBuilder.setRangeZ(selected_slices_str);
            }
        }

        int timepointbegin = rangeBuilder.build().getRangeT().get(0)-1;
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

        if (parallel) locationsIndexes = locationsIndexes.parallel();

        locationsIndexes.forEach(idx -> { // .parallel()
            AffineTransform3D location = indexToLocation.get(idx);
            List<SourceAndConverter> sources = sacSortedPerLocation.get(location).stream().map(sac -> (SourceAndConverter) sac).collect(Collectors.toList());
            ImagePlus imp_out;
            String name = sacSortedPerLocation.get(location).get(0).getSpimSource().getName();
            HyperRange range = rangeBuilder.setRangeC(1,sources.size()).build();
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
            imp_out.show();
        });
        //imps_out.forEach(ImagePlus::show);
    }

    public Function<Collection<SourceAndConverter>,List<SourceAndConverter>> sorter = sacslist -> SourceAndConverterHelper.sortDefaultNoGeneric(sacslist);

}
