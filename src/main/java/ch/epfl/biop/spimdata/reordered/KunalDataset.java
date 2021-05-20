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

    protected static Logger logger = LoggerFactory.getLogger(KunalDataset.class);

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

    Map<Integer, ViewSetup> idToNewViewSetups = new HashMap<>();

    public AbstractSpimData constructSpimData() {

        // No Illumination
        List<ViewSetup> newViewSetups = new ArrayList<>();

        int maxTimePoint = spimdataOrigin
                .getViewRegistrations()
                .getViewRegistrations()
                .keySet()
                .stream().map(this::originToReorderedLocation)
                .mapToInt(viewId -> viewId.getTimePointId())
                .max().getAsInt();

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
                /*.map(this::originToReorderedLocation)
                .forEach(viewId -> {
                    if (!idToNewViewSetups.containsKey(viewId.getViewSetupId())) {
                        BasicViewSetup bvs = (BasicViewSetup) spimdataOrigin.getSequenceDescription().getViewSetups().get(viewId.getViewSetupId());
                        ViewSetup newViewSetup =
                                new ViewSetup(
                                        viewId.getViewSetupId(),
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

                    }
                });*/

        /*spimdataOrigin.getSequenceDescription().getViewSetupsOrdered().stream().forEach(vs -> {
            BasicViewSetup bvs = (BasicViewSetup) vs;
            ViewSetup newViewSetup =
                    new ViewSetup(
                            bvs.getId(),
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
            newViewSetups.add(newViewSetup); // Because Set using equals which uses id, redundant View Setups will disappear
        });*/


        logger.debug("Reordered spimdata number of setups = "+newViewSetups.size());


        /*try {
            for (int iF=0;iF<openers.size();iF++) {
                FileIndex fi = new FileIndex(iF);
                String dataLocation = openers.get( iF ).getDataLocation();
                fi.setName( dataLocation );
                logger.debug("Data located at "+ dataLocation );

                IFormatReader memo = openers.get(iF).getNewReader();

                final int iFile = iF;

                final int seriesCount = memo.getSeriesCount();
                logger.debug("Number of Series " + seriesCount );
                final IMetadata omeMeta = (IMetadata) memo.getMetadataStore();

                fileIdxToNumberOfSeries.put(iF, seriesCount );

                // -------------------------- SETUPS For each Series : one per timepoint and one per channel
                IntStream series = IntStream.range(0, seriesCount );
                series.forEach(iSerie -> {
                    memo.setSeries(iSerie);
                    SeriesNumber sn = new SeriesNumber(iSerie);
                    sn.setName("Series_"+iSerie);
                    fileIdxToNumberOfSeriesAndTimepoints.put(iFile, new SeriesTps( seriesCount,omeMeta.getPixelsSizeT(iSerie).getNumberValue().intValue()));
                    // One serie = one Tile
                    Tile tile = new Tile(nTileCounter);
                    nTileCounter++;
                    // ---------- Serie >
                    // ---------- Serie > Timepoints
                    logger.debug("\t Serie " + iSerie + " Number of timesteps = " + omeMeta.getPixelsSizeT(iSerie).getNumberValue().intValue());
                    // ---------- Serie > Channels
                    logger.debug("\t Serie " + iSerie + " Number of channels = " + omeMeta.getChannelCount(iSerie));
                    //final int iS = iSerie;
                    // Properties of the serie
                    IntStream channels = IntStream.range(0, omeMeta.getChannelCount(iSerie));
                    if (omeMeta.getPixelsSizeT(iSerie).getNumberValue().intValue() > maxTimepoints) {
                        maxTimepoints = omeMeta.getPixelsSizeT(iSerie).getNumberValue().intValue();
                    }
                    String imageName = getImageName( dataLocation, seriesCount, omeMeta, iSerie );
                    Dimensions dims = BioFormatsMetaDataHelper.getSeriesDimensions(omeMeta, iSerie); // number of pixels .. no calibration
                    logger.debug("X:"+dims.dimension(0)+" Y:"+dims.dimension(1)+" Z:"+dims.dimension(2));
                    VoxelDimensions voxDims = BioFormatsMetaDataHelper.getSeriesVoxelDimensions(omeMeta, iSerie, openers.get(iFile).u, openers.get(iFile).voxSizeReferenceFrameLength);
                    // Register Setups (one per channel and one per timepoint)
                    channels.forEach(
                            iCh -> {
                                int ch_id = getChannelId(omeMeta, iSerie, iCh, memo.isRGB());
                                String channelName = getChannelName( omeMeta, iSerie, iCh ) ;

                                String setupName = imageName + "-" + channelName;
                                logger.debug(setupName);
                                ViewSetup vs = new ViewSetup(
                                        viewSetupCounter,
                                        setupName,
                                        dims,
                                        voxDims,
                                        tile, // Tile is index of Serie
                                        channelIdToChannel.get(ch_id),
                                        dummy_ang,
                                        dummy_ill);
                                vs.setAttribute(fi);
                                vs.setAttribute(sn);

                                // Attempt to set color
                                Displaysettings ds = new Displaysettings(viewSetupCounter);
                                ds.min = 0;
                                ds.max = 255;
                                ds.isSet = false;

                                // ----------- Color
                                ARGBType color = BioFormatsMetaDataHelper.getColorFromMetadata(omeMeta, iSerie, iCh);

                                if (color!=null) {
                                    ds.isSet = true;
                                    ds.color = new int[]{
                                            ARGBType.red(color.get()),
                                            ARGBType.green(color.get()),
                                            ARGBType.blue(color.get()),
                                            ARGBType.alpha(color.get())};
                                }
                                vs.setAttribute(ds);

                                viewSetups.add(vs);
                                viewSetupToBFFileSerieChannel.put(viewSetupCounter, new FileSerieChannel(iFile, iSerie, iCh));
                                viewSetupCounter++;

                            });
                });
                memo.close();
            }*/

            // ------------------- BUILDING SPIM DATA


           /* timePoints.forEach(iTp -> {
                viewSetupToBFFileSerieChannel
                        .keySet()
                        .stream()
                        .forEach(viewSetupId -> {
                            if (iTp.getId()<nTimepoints) {
                                registrations.add(new ViewRegistration(iTp.getId(), viewSetupId, BioFormatsMetaDataHelper.getSeriesRootTransform(
                                        omeMeta,
                                        iSerie,
                                        openers.get(iFile).u,
                                        openers.get(iFile).positionPreTransformMatrixArray, //AffineTransform3D positionPreTransform,
                                        openers.get(iFile).positionPostTransformMatrixArray, //AffineTransform3D positionPostTransform,
                                        openers.get(iFile).positionReferenceFrameLength,
                                        openers.get(iFile).positionIsImageCenter, //boolean positionIsImageCenter,
                                        openers.get(iFile).voxSizePreTransformMatrixArray, //voxSizePreTransform,
                                        openers.get(iFile).voxSizePostTransformMatrixArray, //AffineTransform3D voxSizePostTransform,
                                        openers.get(iFile).voxSizeReferenceFrameLength, //null, //Length voxSizeReferenceFrameLength,
                                        openers.get(iFile).axesOfImageFlip // axesOfImageFlip
                                )));
                            } else {
                                missingViews.add(new ViewId(iTp.getId(), viewSetupId));
                            }
                        });
            });*/

            SequenceDescription sd = new SequenceDescription( new TimePoints( timePoints ), newViewSetups , null, new MissingViews(missingViews));
            sd.setImgLoader(new ReorderedImageLoader(this, sd, 4, 2));

            final SpimData spimData = new SpimData( null, sd, new ViewRegistrations( registrations ) );
            return spimData;
    }

}
