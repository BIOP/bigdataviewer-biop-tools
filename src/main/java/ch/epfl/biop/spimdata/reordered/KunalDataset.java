package ch.epfl.biop.spimdata.reordered;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * The famous Kunal makes gigantic lif images on the SP8.
 *
 * The issue is that these images have timepoints splitted along series
 *
 * This class is using {@link ReorderedImageLoader} in order to turn viewsetups into extra timepoints
 *
 */

public class KunalDataset implements ISetupOrder {

    transient AbstractSpimData spimdataOrigin;

    transient protected static Logger logger = LoggerFactory.getLogger(KunalDataset.class);

    final int nTiles;

    final int nChannels;

    final String spimdataOriginPath;

    public KunalDataset(String spimdataOriginPath, int nTiles, int nChannels) {
        this.spimdataOriginPath = spimdataOriginPath;
        this.nTiles = nTiles;
        this.nChannels = nChannels;
    }

    @Override
    public void initialize() {
        try {
            spimdataOrigin = new XmlIoSpimData().load(spimdataOriginPath);
        } catch (SpimDataException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ReorderedImageLoader.SpimDataViewId getOriginalLocation(ViewId viewId) {
        ReorderedImageLoader.SpimDataViewId svi = new ReorderedImageLoader.SpimDataViewId();
        svi.asd = spimdataOrigin;
        int timePointOrigin = 0; // Only one timepoint at origin
        int viewSetupOrigin = (viewId.getTimePointId() * (nTiles*nChannels)) + viewId.getViewSetupId();
        svi.viewId = new ViewId(timePointOrigin, viewSetupOrigin);
        logger.debug("Current: [t = "+viewId.getTimePointId()+" s = "+viewId.getViewSetupId()+"] <- Origin [t = "+timePointOrigin+" s = "+viewSetupOrigin+"]");
        return svi;
    }

    public ViewId originToReorderedLocation(ViewId viewId) {
        int newTimePoint = viewId.getViewSetupId() / (nTiles*nChannels);
        int newSetupId = viewId.getViewSetupId() % (nTiles*nChannels);
        return new ViewId(newTimePoint, newSetupId);
    }

    public AbstractSpimData constructSpimData() {

        Map<Integer, ViewSetup> idToNewViewSetups = new HashMap<>();
        // No Illumination
        List<ViewSetup> newViewSetups = new ArrayList<>();

        int maxTimePoint = spimdataOrigin
                .getViewRegistrations()
                .getViewRegistrations()
                .keySet()
                .stream().map(this::originToReorderedLocation)
                .mapToInt(viewId -> viewId.getTimePointId())
                .max().getAsInt()+1;

        logger.debug("Reordered spimdata max timepoint = "+maxTimePoint);

        List<TimePoint> timePoints = new ArrayList<>();
        IntStream.range(0,maxTimePoint).forEach(tp -> timePoints.add(new TimePoint(tp)));

        final ArrayList<ViewRegistration> registrations = new ArrayList<>();

        List<ViewId> missingViews = new ArrayList<>();

        spimdataOrigin
                .getViewRegistrations()
                .getViewRegistrations()
                .keySet()
                .stream()
                .forEach(originViewId -> {
                    ViewId newViewId = originToReorderedLocation(originViewId);
                    // Create ViewSetup if it doesn't exist
                    if (!idToNewViewSetups.containsKey(newViewId.getViewSetupId())) {
                        BasicViewSetup bvs = (BasicViewSetup) spimdataOrigin.getSequenceDescription().getViewSetups().get(originViewId.getViewSetupId());
                        ViewSetup newViewSetup =
                                new ViewSetup(
                                        newViewId.getViewSetupId(),
                                        bvs.getName(),
                                        bvs.getSize(),
                                        bvs.getVoxelSize(),
                                        bvs.getAttribute(Channel.class),
                                        bvs.getAttribute(Angle.class),
                                        bvs.getAttribute(Illumination.class));

                        bvs.getAttributes().forEach((name,entity) -> {
                            logger.debug("ViewSetupId : "+bvs.getId()+" "+name+":"+entity);
                            newViewSetup.setAttribute(entity);
                        });
                        newViewSetups.add(newViewSetup);
                        idToNewViewSetups.put(newViewId.getViewSetupId(), newViewSetup);
                    }

                    // Create View Registration
                    registrations.add(
                            new ViewRegistration(newViewId.getTimePointId(),
                                    newViewId.getViewSetupId(),
                                    spimdataOrigin.getViewRegistrations()
                                            .getViewRegistration(originViewId)
                                            .getModel()
                            )
                    );
                });

        newViewSetups.forEach(vs -> logger.debug(vs.getName()));

        logger.debug("Reordered spimdata number of setups = "+newViewSetups.size());

        SequenceDescription sd = new SequenceDescription( new TimePoints( timePoints ), newViewSetups , null, new MissingViews(missingViews));
        sd.setImgLoader(new ReorderedImageLoader(this, sd, 4, 2));

        final SpimData spimData = new SpimData( null, sd, new ViewRegistrations( registrations ) );
        return spimData;
    }

}
