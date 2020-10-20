package ch.epfl.biop.spimdata.imageplus;

import bdv.util.ImageStackImageLoaderTimeShifted;
import bdv.util.VirtualStackImageLoaderTimeShifted;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderIo;
import mpicbg.spim.data.generic.sequence.XmlIoBasicImgLoader;
import org.jdom2.Element;

import java.io.File;

import static mpicbg.spim.data.XmlKeys.IMGLOADER_FORMAT_ATTRIBUTE_NAME;

@ImgLoaderIo( format = "spimreconstruction.biop_virtualstackimageplusimageloader", type = VirtualStackImageLoaderTimeShifted.class )
public class XmlIoVirtualStackImagePlusLoader implements XmlIoBasicImgLoader< VirtualStackImageLoaderTimeShifted > {

    final public static String IMAGEPLUS_FILEPATH_TAG = "imageplus_filepath";
    final public static String IMAGEPLUS_TIME_ORIGIN_TAG = "imageplus_time_origin";

    @Override
    public Element toXml(VirtualStackImageLoaderTimeShifted imgLoader, File basePath) {
        final Element elem = new Element( "ImageLoader" );
        elem.setAttribute( IMGLOADER_FORMAT_ATTRIBUTE_NAME, this.getClass().getAnnotation( ImgLoaderIo.class ).format() );
        // For potential extensibility
        elem.addContent(XmlHelpers.textElement( IMAGEPLUS_FILEPATH_TAG, imgLoader.getImagePlus().getFileInfo().getFilePath()));
        elem.addContent(XmlHelpers.intElement( IMAGEPLUS_TIME_ORIGIN_TAG, imgLoader.getTimeShift()));
        return elem;
    }

    @Override
    public VirtualStackImageLoaderTimeShifted fromXml(Element elem, File basePath, AbstractSequenceDescription<?, ?, ?> sequenceDescription) {
        String imagePlusFilePath = XmlHelpers.getText( elem, IMAGEPLUS_FILEPATH_TAG );
        int originTimePoint = XmlHelpers.getInt(elem, IMAGEPLUS_TIME_ORIGIN_TAG);

        ImagePlus imp = IJ.openImage(imagePlusFilePath);

        final VirtualStackImageLoaderTimeShifted imgLoader;

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

        return imgLoader;
    }
}
