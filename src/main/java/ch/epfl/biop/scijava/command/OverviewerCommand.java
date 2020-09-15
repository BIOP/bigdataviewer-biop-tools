package ch.epfl.biop.scijava.command;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
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

        Map<SacProperties, List<SourceAndConverter>> sacClasses = sourceList
                .stream()
                .collect(Collectors.groupingBy(sac -> new SacProperties(sac)));

        Map<SourceAndConverter<?>, List<SacProperties>> keySetSac = sacClasses.keySet().stream().collect(Collectors.groupingBy(p -> p.sac));

        List<SourceAndConverter<?>> sortedSacs = sorter.apply(keySetSac.keySet());

        List<SourceAndConverter> sacsToDisplay = new ArrayList<>();

        sortedSacs.forEach(sacKey -> {
            SacProperties sacPropsKey = keySetSac.get(sacKey).get(0);
            AffineTransform3D location = sacPropsKey.location;

            int xPos = currentIndex % nColumns;
            int yPos = currentIndex / nColumns;

            currentAffineTransform.identity();
            currentAffineTransform.preConcatenate(location.inverse());
            AffineTransform3D translator = new AffineTransform3D();
            translator.translate(xPos, yPos,0);

            currentIndex++;

            List<SourceAndConverter> sacs = sacClasses.get(sacPropsKey);// sacSortedPerLocation.get(location);

            long nPixX = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(0);

            long nPixY = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(1);

            long nPixZ = sacs.get(0).getSpimSource().getSource(timepointBegin, 0).dimension(2);

            long sizeMax = Math.max(nPixX, nPixY);

            sizeMax = Math.max(sizeMax, nPixZ);

            currentAffineTransform.scale(1/(double)sizeMax, 1/(double) sizeMax, 1/(double)sizeMax);

            currentAffineTransform.translate(xPos,yPos,0);

            SourceAffineTransformer sat = new SourceAffineTransformer(null, currentAffineTransform);

            List<SourceAndConverter> transformedSacs =
                    sacs.stream().map(sac -> sat.apply(sac)).collect(Collectors.toList());

            sacsToDisplay.addAll(transformedSacs);
        });

        SourceAndConverterServices
                .getSourceAndConverterDisplayService()
                .show(bdvh, sacsToDisplay.toArray(new SourceAndConverter[sacsToDisplay.size()]));

        AffineTransform3D currentViewLocation = new AffineTransform3D();

        bdvh.getViewerPanel().state().getViewerTransform(currentViewLocation);
        currentViewLocation.set(0,2,3);
        bdvh.getViewerPanel().state().setViewerTransform(currentViewLocation);


    }

    public Function<Collection<SourceAndConverter<?>>,List<SourceAndConverter<?>>> sorter = sacs1ist -> SourceAndConverterUtils.sortDefaultGeneric(sacs1ist);

    class SacProperties {

        final AffineTransform3D location;
        long[] dims = new long[3];
        SourceAndConverter sac;

        public SacProperties(SourceAndConverter sac) {
            location = new AffineTransform3D();
            sac.getSpimSource().getSourceTransform(timepointBegin, 0, location);
            sac.getSpimSource().getSource(timepointBegin,0).dimensions(dims);
            this.sac = sac;
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 89  * hash + (int) dims[0] + 17 * (int) dims[1] + 57 * (int) dims[2];
            hash = hash + (int) (10 * location.get(0,0));
            return hash;
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
