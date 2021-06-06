package ch.epfl.biop.bdv.command.exporter;

import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import spimdata.imageplus.ImagePlusHelper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static sc.fiji.bdvpg.bdv.navigate.ViewerTransformSyncStopper.MatrixApproxEquals;

@Plugin(type = BdvPlaygroundActionCommand.class, menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Show Sources (split) (IJ1)")
public class ExportToMultipleImagePlusCommand implements BdvPlaygroundActionCommand {

    @Parameter
    public SourceAndConverter[] sacs;

    @Parameter
    public int level;

    @Parameter
    public int timepointBegin;

    @Parameter
    public int timepointEnd;

    @Parameter(type = ItemIO.OUTPUT)
    public List<ImagePlus> imps_out = new ArrayList<>();

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
            mapSacToCs.put(sac,SourceAndConverterServices.getBdvDisplayService().getConverterSetup(sac));
            mapSacToMml.put(sac, level);
        }

        sourceList = sorter.apply(Arrays.asList(sacs));

        // Sort according to location = affine transform 3d of sources

        List<AffineTransform3D> locations = sourceList.stream().map(sac -> {
            AffineTransform3D at3d = new AffineTransform3D();
            sac.getSpimSource().getSourceTransform(timepointBegin, level, at3d);
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

        List<SourceAndConverter<?>> sacs0nonSorted = new ArrayList<>();

        sacSortedPerLocation.values().stream().map(l -> l.get(0)).forEach(sac -> sacs0nonSorted.add(sac));

        List<SourceAndConverter<?>> sacs0Sorted = sorter.apply(sacs0nonSorted);

        Map<Integer, AffineTransform3D> indexToLocation = new HashMap<>();

        sacSortedPerLocation.keySet().stream().forEach(location -> {
            SourceAndConverter sac0 = sacSortedPerLocation.get(location).get(0);
            indexToLocation.put(sacs0Sorted.indexOf(sac0), location);
        });

        indexToLocation.keySet().stream().sorted().forEach(idx -> {
            AffineTransform3D location = indexToLocation.get(idx);
        //sacSortedPerLocation.keySet().stream().forEach(location -> {

            ImagePlus imp_out = ImagePlusHelper.wrap(sacSortedPerLocation.get(location).stream().map(sac -> (SourceAndConverter) sac).collect(Collectors.toList()), mapSacToCs, mapSacToMml, timepointBegin, timepointEnd, false );

            AffineTransform3D at3D = new AffineTransform3D();
            sacSortedPerLocation.get(location).get(0).getSpimSource().getSourceTransform(timepointBegin, level, at3D);

            String unit = "px";

            if (sacSortedPerLocation.get(location).get(0).getSpimSource().getVoxelDimensions() != null) {
                unit = sacSortedPerLocation.get(location).get(0).getSpimSource().getVoxelDimensions().unit();
                if (unit==null) {
                    unit = "px";
                }
            }

            imp_out.setTitle(sacSortedPerLocation.get(location).get(0).getSpimSource().getName());

            ImagePlusHelper.storeExtendedCalibrationToImagePlus(imp_out,at3D,unit,timepointBegin);

            imps_out.add(imp_out);

        });

        imps_out.forEach(ImagePlus::show);
    }


    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacs1ist -> SourceAndConverterHelper.sortDefaultGeneric(sacs1ist);

}
