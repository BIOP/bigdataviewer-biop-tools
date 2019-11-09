package ch.epfl.biop.bdv.multisourcealign;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvStackSource;
import ch.epfl.biop.bdv.bioformats.BioformatsBdvDisplayHelper;
import ch.epfl.biop.bdv.bioformats.imageloader.BioFormatsImageLoader;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.cache.GuavaWeakCacheService;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ch.epfl.biop.bdv.scijava.command.Info.ScijavaBdvRootMenu;

@Plugin(type = Command.class, menuPath = ScijavaBdvRootMenu+"Registration>Open Scans And Align In Z")
public class OpenSlidesAndAlign implements Command {

    @Parameter
    double zStartingLocation = 0;

    @Parameter
    double zSliceSize = 0.01;

    @Parameter
    double valMin;

    @Parameter
    double valMax;

    @Parameter
    int timePoint = 0;

    @Parameter
    File file;

    @Parameter
    String entitiesToAlign = "tile";

    @Parameter
    String entityUsedForAlignement = "channel";

    @Parameter
    int idOfEntityUsedForAlignment = 2;

    @Parameter(label = "BigDataViewer Frame", type = ItemIO.OUTPUT)
    public BdvHandle bdv_h;

    @Parameter(label = "BigDataViewer Frame", type = ItemIO.OUTPUT)
    public AbstractSpimData asd;

    @Parameter
    GuavaWeakCacheService cs;

    int counter = 0;

    @Override
    public void run() {

        final SpimDataMinimal spimData;
        try {
            // Load dataset
            spimData = new XmlIoSpimDataMinimal().load( file.getAbsolutePath() );

            // TODO : replace this fake entities by the correct ones
            ArrayList<Integer>  entitiesIndexInOrder = new ArrayList<>();
            for (int i=0;i<spimData.getSequenceDescription().getViewSetups().size();i++) {
                entitiesIndexInOrder.add(i);
            }

            Map<Integer, Integer> indexInZToViewSetup = new HashMap<>();
            Map<Integer, List<Integer>> viewSetupToFollowingViewSetup = new HashMap<>();

            spimData.getSequenceDescription().getViewSetupsOrdered().forEach(vs -> {
                if (vs.getAttributes().containsKey(entitiesToAlign)) {
                    if (entitiesIndexInOrder.contains(vs.getAttributes().get(entitiesToAlign).getId())) {
                    if (vs.getAttributes().containsKey(entityUsedForAlignement)) {
                        if (vs.getAttributes().get(entityUsedForAlignement).getId()==idOfEntityUsedForAlignment) {
                            entitiesIndexInOrder.add(vs.getAttributes().get(entitiesToAlign).getId());

                            indexInZToViewSetup.put(counter, vs.getId()); //
                            counter++;
                        }
                    }
                    }
                }
            });

            indexInZToViewSetup.values().stream().map(id -> spimData.getSequenceDescription().getViewSetups().get(id)).forEach(
                    vs -> {
                        int idEntitiesToAlign = vs.getAttributes().get(entitiesToAlign).getId();
                        List<Integer> indexesOfViewSetupThatFollowTransformation = spimData.getSequenceDescription().getViewSetups().values().stream().filter(
                                vstest ->
                                        (vstest.getAttributes().get(entitiesToAlign).getId() == idEntitiesToAlign) && (vs.getId()!=vstest.getId())
                        ).map(vstest -> vstest.getId()).collect(Collectors.toList());
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
                    at3DToOrigin.translate(0,0,zStartingLocation+entitiesIndexInOrder.indexOf(eIndex)*zSliceSize);


                    AffineTransform3D at3DCenter = new AffineTransform3D();
                    at3DCenter.translate(-sx/2, -sy/2,0);

                    ViewTransform toOrigin = new ViewTransformAffine("toOrigin", at3DToOrigin);
                    ViewTransform centerImage = new ViewTransformAffine("centerImage", at3DCenter);

                    spimData.getViewRegistrations().getViewRegistration(timePoint,iViewSetup).preconcatenateTransform(toOrigin);
                    spimData.getViewRegistrations().getViewRegistration(timePoint,iViewSetup).concatenateTransform(centerImage);

                    if (viewSetupToFollowingViewSetup.get(iViewSetup)!=null) {
                        viewSetupToFollowingViewSetup.get(iViewSetup).forEach(ivs -> {
                                    spimData.getViewRegistrations().getViewRegistration(timePoint, ivs).preconcatenateTransform(toOrigin);
                                    spimData.getViewRegistrations().getViewRegistration(timePoint, ivs).concatenateTransform(centerImage);
                                }
                        );
                    }
                }
            }

            List<BdvStackSource<?>> lbss = BdvFunctions.show(spimData);

            if (spimData.getSequenceDescription().getImgLoader() instanceof BioFormatsImageLoader) {
                BioformatsBdvDisplayHelper.autosetColorsAngGrouping(lbss, spimData, true, valMin, valMax, true);
            }

            // Output
            asd = spimData;
            bdv_h = lbss.get(0).getBdvHandle();

            // Keep link between viewer and spimdata in cacheservice -> updates viewer in case of necessity
            cs.put(asd,lbss);

            /* example if one need to update the viewer after asd has been changed
            Method method = Class.forName("bdv.AbstractSpimSource").getDeclaredMethod("loadTimepoint", int.class);
            method.setAccessible(true);

            for (int i=0;i<lbss.size();i+=2) {
                spimData.getViewRegistrations().getViewRegistration(timePoint, i).preconcatenateTransform(vt);
                TransformedSource src = (TransformedSource) lbss.get(i).getSources().get(0).getSpimSource();
                AbstractSpimSource ass = (AbstractSpimSource) src.getWrappedSource();

                method.invoke(ass, timePoint);

                src = (TransformedSource) lbss.get(i).getSources().get(0).asVolatile().getSpimSource();
                ass = (AbstractSpimSource) src.getWrappedSource();

                method.invoke(ass, timePoint);
            }

        ViewTransform vt = new ViewTransform() {
            @Override
            public boolean hasName() {
                return false;
            }

            @Override
            public String getName() {
                return "";
            }

            @Override
            public AffineGet asAffine3D() {
                AffineTransform3D at3D = new AffineTransform3D();
                at3D.scale(2,1,1);
                return at3D;
            }
        };

            */

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
