package ch.epfl.biop.bdv.transform.multisourcealign;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Plugin(type = Command.class, menuPath = "BigDataViewer>SpimDataset>Register>Center and Display Setups Along Z")
public class RecenterSpimDataImages implements Command {

    @Parameter
    double zStartingLocation = 0;

    @Parameter
    double zSliceSize = 0.01;

    @Parameter
    int timePoint = 0;

    @Parameter
    String entitiesToAlign = "tile";

    @Parameter
    String entityUsedForAlignement = "channel";

    @Parameter
    int idOfEntityUsedForAlignment = 2;

    @Parameter(label = "SpimDataset", type = ItemIO.BOTH)
    public AbstractSpimData<AbstractSequenceDescription<BasicViewSetup,?,?>> spimData;

    int counter = 0;

    @Parameter
    CommandService cs;

    @Override
    public void run() {

            // TODO : replace this fake entities by the correct ones
            ArrayList<Integer>  entitiesIndexInOrder = new ArrayList<>();
            for (int i=0;i<spimData.getSequenceDescription().getViewSetups().size();i++) {
                entitiesIndexInOrder.add(i);
            }

            Map<Integer, Integer> indexInZToViewSetup = new HashMap<>();
            Map<Integer, List<Integer>> viewSetupToFollowingViewSetup = new HashMap<>();

            spimData.getSequenceDescription().getViewSetupsOrdered().forEach(vs -> {
                if (vs.getAttributes().containsKey(entitiesToAlign)) {
                    if (entitiesIndexInOrder.contains((vs.getAttributes().get(entitiesToAlign).getId()))) {
                    if (vs.getAttributes().containsKey(entityUsedForAlignement)) {
                        if (vs.getAttributes().get(entityUsedForAlignement).getId()==idOfEntityUsedForAlignment) {
                            entitiesIndexInOrder.add(vs.getAttributes().get(entitiesToAlign).getId());
                            indexInZToViewSetup.put(counter, (vs.getId())); //
                            counter++;
                        }
                    }
                    }
                }
            });

            indexInZToViewSetup.values().stream().map(id -> spimData.getSequenceDescription().getViewSetups().get(id)).forEach(
                    vs -> {
                        int idEntitiesToAlign = vs.getAttributes().get(entitiesToAlign).getId();
                        List<Integer> indexesOfViewSetupThatFollowTransformation =
                                spimData.getSequenceDescription()
                                        .getViewSetups()
                                        .values()
                                        .stream()
                                        .filter(
                                            vstest -> (vstest.getAttributes().get(entitiesToAlign).getId() == idEntitiesToAlign)
                                                    && (vs.getId()!=vstest.getId()))
                                        .map(vstest -> vstest.getId()).collect(Collectors.toList());
                        viewSetupToFollowingViewSetup.put( vs.getId(), indexesOfViewSetupThatFollowTransformation);
                    }
            );

            for (Integer eIndex:entitiesIndexInOrder) {
                if (indexInZToViewSetup.containsKey(eIndex)) {
                    int iViewSetup = indexInZToViewSetup.get(eIndex);

                    long sx = spimData.getSequenceDescription().getViewSetupsOrdered().get(iViewSetup).getSize().dimension(0);

                    long sy = spimData.getSequenceDescription().getViewSetupsOrdered().get(iViewSetup).getSize().dimension(1);

                    AffineTransform3D at3D = spimData.getViewRegistrations().getViewRegistration(timePoint,iViewSetup).getModel();

                    AffineTransform3D at3DToOrigin = new AffineTransform3D();
                    at3DToOrigin.translate(-at3D.get(0,3), -at3D.get(1,3), -at3D.get(2,3));
                    at3DToOrigin.scale(1d,1d,zSliceSize/(at3D.get(2,2)));
                    at3DToOrigin.translate(0,0,zStartingLocation+entitiesIndexInOrder.indexOf(eIndex)*zSliceSize*0);

                    AffineTransform3D at3DCenter = new AffineTransform3D();
                    at3DCenter.translate(-sx/2, -sy/2,0);

                    ViewTransform toOrigin = new ViewTransformAffine("toOrigin", at3DToOrigin);
                    ViewTransform centerImage = new ViewTransformAffine("centerImage", at3DCenter);

                    spimData.getViewRegistrations().getViewRegistration(timePoint,iViewSetup).preconcatenateTransform(toOrigin);
                    spimData.getViewRegistrations().getViewRegistration(timePoint,iViewSetup).concatenateTransform(centerImage);
                    spimData.getViewRegistrations().getViewRegistration(timePoint,iViewSetup).updateModel();

                    if (viewSetupToFollowingViewSetup.get(iViewSetup)!=null) {
                        viewSetupToFollowingViewSetup.get(iViewSetup).forEach(ivs -> {
                                    spimData.getViewRegistrations().getViewRegistration(timePoint, ivs).preconcatenateTransform(toOrigin);
                                    spimData.getViewRegistrations().getViewRegistration(timePoint, ivs).concatenateTransform(centerImage);
                                    spimData.getViewRegistrations().getViewRegistration(timePoint, ivs).updateModel();
                                }
                        );
                    }
                }
            }
            //cs.run(SpimdatasetUpdateBdvWindow.class, true, "timePoint", timePoint, "spimData", spimData);
            // TODO : Fix this : update bdv where sources of the spimdata are displayed
    }
}
