package ch.epfl.biop.spimdata.imageplus;

import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.util.ImagePlusHelper;
import bdv.util.ImageStackImageLoaderTimeShifted;
import bdv.util.VirtualStackImageLoaderTimeShifted;
import ij.ImagePlus;
import ij.plugin.ChannelSplitter;
import ij.process.LUT;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.*;
import net.imglib2.FinalDimensions;
import net.imglib2.realtransform.AffineTransform3D;
import spimdata.util.Displaysettings;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class SpimDataFromImagePlusGetter implements Runnable, Function<ImagePlus, AbstractSpimData> {

    static Consumer<String> errlog = (str) -> System.err.println(SpimDataFromImagePlusGetter.class.getSimpleName()+" ERROR : "+str);

    public SpimDataFromImagePlusGetter() {

    }

    @Override
    public void run() {
    }

    // Function stolen and modified from bigdataviewer_fiji
    public AbstractSpimData< ? > apply(ImagePlus imp)
    {
        // check the image type
        switch ( imp.getType() )
        {
            case ImagePlus.GRAY8:
            case ImagePlus.GRAY16:
            case ImagePlus.GRAY32:
            case ImagePlus.COLOR_RGB:
                break;
            default:
                errlog.accept( imp.getShortTitle() + ": Only 8, 16, 32-bit images and RGB images are supported currently!" );
                return null;
        }

        // get calibration and image size
        final double pw = imp.getCalibration().pixelWidth;
        final double ph = imp.getCalibration().pixelHeight;
        final double pd = imp.getCalibration().pixelDepth;
        String punit = imp.getCalibration().getUnit();
        if ( punit == null || punit.isEmpty() )
            punit = "px";
        final FinalVoxelDimensions voxelSize = new FinalVoxelDimensions( punit, pw, ph, pd );
        final int w = imp.getWidth();
        final int h = imp.getHeight();
        final int d = imp.getNSlices();
        final FinalDimensions size = new FinalDimensions( w, h, d );

        // propose reasonable mipmap settings
//		final ExportMipmapInfo autoMipmapSettings = ProposeMipmaps.proposeMipmaps( new BasicViewSetup( 0, "", size, voxelSize ) );

        // create ImgLoader wrapping the image

        int originTimePoint = ImagePlusHelper.getTimeOriginFromImagePlus(imp);
        final BasicImgLoader imgLoader;
        if ( imp.getStack().isVirtual() )
        {
            switch ( imp.getType() )
            {
                case ImagePlus.GRAY8:
                    imgLoader = VirtualStackImageLoaderTimeShifted.createUnsignedByteInstance( imp, originTimePoint );
                    break;
                case ImagePlus.GRAY16:
                    imgLoader = VirtualStackImageLoaderTimeShifted.createUnsignedShortInstance( imp, originTimePoint );
                    break;
                case ImagePlus.GRAY32:
                    imgLoader = VirtualStackImageLoaderTimeShifted.createFloatInstance( imp, originTimePoint );
                    break;
                case ImagePlus.COLOR_RGB:
                default:
                    imgLoader = VirtualStackImageLoaderTimeShifted.createARGBInstance( imp, originTimePoint );
                    break;
            }
        }
        else
        {
            switch ( imp.getType() )
            {
                case ImagePlus.GRAY8:
                    imgLoader = ImageStackImageLoaderTimeShifted.createUnsignedByteInstance( imp, originTimePoint );
                    break;
                case ImagePlus.GRAY16:
                    imgLoader = ImageStackImageLoaderTimeShifted.createUnsignedShortInstance( imp, originTimePoint );
                    break;
                case ImagePlus.GRAY32:
                    imgLoader = ImageStackImageLoaderTimeShifted.createFloatInstance( imp, originTimePoint );
                    break;
                case ImagePlus.COLOR_RGB:
                default:
                    imgLoader = ImageStackImageLoaderTimeShifted.createARGBInstance( imp, originTimePoint );
                    break;
            }
        }

        final int numTimepoints = imp.getNFrames();
        final int numSetups = imp.getNChannels();

        // create setups from channels
        final HashMap< Integer, BasicViewSetup > setups = new HashMap<>( numSetups );

        ImagePlus[] impSingleChannel = ChannelSplitter.split(imp);
        for ( int s = 0; s < numSetups; ++s )
        {
            final BasicViewSetup setup = new BasicViewSetup( s, String.format( imp.getTitle() + " channel %d", s + 1 ), size, voxelSize );
            setup.setAttribute( new Channel( s + 1 ) );
            Displaysettings ds = new Displaysettings(s+1);
            ds.min = impSingleChannel[s].getDisplayRangeMin();
            ds.max = impSingleChannel[s].getDisplayRangeMax();
            if (imp.getType() == ImagePlus.COLOR_RGB) {
                ds.isSet = false;
            } else {
                ds.isSet = true;
                LUT lut = impSingleChannel[s].getProcessor().getLut();
                ds.color = new int[]{lut.getRed(255), lut.getGreen(255), lut.getBlue(255), lut.getAlpha(255)};
            }
            setup.setAttribute(ds);
            setups.put( s, setup );
        }

        // create timepoints
        final ArrayList<TimePoint> timepoints = new ArrayList<>( numTimepoints );

        MissingViews mv = null;

        if (originTimePoint>0) {

            Set<ViewId> missingViewIds = new HashSet<>();
            for (int t=0;t<originTimePoint;t++) {
                for ( int s = 0; s < numSetups; ++s ){
                    ViewId vId = new ViewId(t,s);
                    missingViewIds.add(vId);
                }
            }
            mv =  new MissingViews(missingViewIds);
        }

        for ( int t = 0; t < numTimepoints + originTimePoint; ++t )
            timepoints.add( new TimePoint( t ) );
        final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepoints ), setups, imgLoader, mv );

        // create ViewRegistrations from the images calibration
        final AffineTransform3D sourceTransform = ImagePlusHelper.getMatrixFromImagePlus(imp);
        final ArrayList<ViewRegistration> registrations = new ArrayList<>();
        for ( int t = 0; t < numTimepoints + originTimePoint; ++t )
            for ( int s = 0; s < numSetups; ++s )
                registrations.add( new ViewRegistration( t , s, sourceTransform ) );

        final File basePath = new File( "." );

        final AbstractSpimData< ? > spimData = new SpimDataMinimal( basePath, seq, new ViewRegistrations( registrations ) );


        return spimData;
    }

}
