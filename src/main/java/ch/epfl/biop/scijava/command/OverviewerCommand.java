package ch.epfl.biop.scijava.command;

import bdv.util.BdvHandle;
import bdv.util.ImagePlusHelper;
import bdv.viewer.SourceAndConverter;
import ij.ImagePlus;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterUtils;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static sc.fiji.bdvpg.bdv.navigate.ViewerTransformSyncStopper.MatrixApproxEquals;

@Plugin(type = Command.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Bdv Overview")
public class OverviewerCommand implements Command {

    @Parameter
    public BdvHandle bdvh;

    @Parameter
    public SourceAndConverter[] sacs;

    @Parameter
    int timepointBegin;

    @Parameter
    int nColumns;

    List<SourceAndConverter<?>> sourceList;

    int currentIndex = 0;
    AffineTransform3D currentAffineTransform = new AffineTransform3D();

    @Override
    public void run() {

        // Sort according to location = affine transform 3d of sources

        sourceList = sorter.apply(Arrays.asList(sacs));

        List<AffineTransform3D> locations = sourceList.stream().map(sac -> {
            AffineTransform3D at3d = new AffineTransform3D();
            sac.getSpimSource().getSourceTransform(timepointBegin, 0, at3d);
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

        List<SourceAndConverter> sacsToDisplay = new ArrayList<>();

        sacSortedPerLocation.keySet().stream().forEach(location -> {

            int xPos = currentIndex % nColumns;
            int yPos = currentIndex / nColumns;

            currentAffineTransform.identity();
            currentAffineTransform.preConcatenate(location.inverse());
            AffineTransform3D translator = new AffineTransform3D();
            translator.translate(xPos, yPos,0);

            currentIndex++;

            List<SourceAndConverter> sacs = sacSortedPerLocation.get(location);

            long nPixX = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(0);

            long nPixY = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(1);

            long nPixZ = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(2);

            currentAffineTransform.scale(1/(double)nPixX, 1/(double) nPixY, 1/(double)nPixZ);

            currentAffineTransform.translate(xPos,yPos,0);

            SourceAffineTransformer sat = new SourceAffineTransformer(null, currentAffineTransform);

            sacsToDisplay.addAll(sacs.stream().map(sat::apply).collect(Collectors.toList()));

            //.forEach().collect(Collectors.toList())

            /*

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

            imps_out.add(imp_out);*/

        });

        SourceAndConverterServices
                .getSourceAndConverterDisplayService()
                .show(bdvh, sacsToDisplay.toArray(new SourceAndConverter[sacsToDisplay.size()]));

        /*bdvh.getViewerPanel().state().addSources(sacsToDisplay);*/
    }

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacs1ist -> SourceAndConverterUtils.sortDefaultGeneric(sacs1ist);


    class SacProperties {

        final AffineTransform3D location;
        long[] dims = new long[3];

        public SacProperties(SourceAndConverter sac) {
            location = new AffineTransform3D();
            sac.getSpimSource().getSourceTransform(timepointBegin, 0, location);
            sac.getSpimSource().getSource(timepointBegin,0).dimensions(dims);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SacProperties) {
                SacProperties other = (SacProperties) obj;
                if  (
                      (MatrixApproxEquals(location.getRowPackedCopy(), other.location.getRowPackedCopy()))
                    &&(dims[0]==other.dims[0])&&(dims[1]==other.dims[1])&&(dims[2]==other.dims[2])) {
                    return true;
                } else {
                    return false;
                }

            } else {
                return false;
            }
        }

    }
}
