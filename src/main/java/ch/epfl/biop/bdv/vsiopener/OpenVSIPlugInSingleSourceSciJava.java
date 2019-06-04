package ch.epfl.biop.bdv.vsiopener;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.SharedQueue;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileARGBType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import ome.units.UNITS;
import java.awt.Color;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.logging.Logger;

@Plugin(type = Command.class,menuPath = "Plugins>BigDataViewer>SciJava>Open VSI Single Source (experimental) (SciJava)")
public class OpenVSIPlugInSingleSourceSciJava implements Command {
    private static final Logger LOGGER = Logger.getLogger( OpenVSIPlugInSingleSourceSciJava.class.getName() );

    @Parameter(label = "VSI Image File")
    public File inputFile;

    @Parameter(label = "Open in new BigDataViewer window")
    public boolean createNewWindow;

    // ItemIO.BOTH required because it can be modified in case of appending new data to BDV (-> requires INPUT), or created (-> requires OUTPUT)
    @Parameter(label = "BigDataViewer Frame", type = ItemIO.BOTH, required = false)
    public BdvHandle bdv_h;

    @Parameter(label="Display type ()", choices = {"Volatile","Standard"})
    public String appendMode = "Volatile";

    @Parameter
    public int sourceIndex;

    @Parameter
    public int channelIndex;

    @Parameter
    public boolean switchZandC = false;

    @Parameter
    public boolean autoscale = true;

    @Override
    public void run()
    {
        BdvOptions options = BdvOptions.options();
        if (createNewWindow) {
            bdv_h=null;
        }

        if (createNewWindow == false && bdv_h!=null) {
            options.addTo(bdv_h);
        }
        try {
            final ImageReader readerIdx = new ImageReader();
            readerIdx.setFlattenedResolutions(false);
            final IMetadata omeMetaIdxOmeXml = MetadataTools.createOMEXMLMetadata();
            readerIdx.setMetadataStore(omeMetaIdxOmeXml);
            readerIdx.setId(inputFile.getAbsolutePath());

            VSIBdvSource bdvSrc = null;

            LOGGER.info("src idx = "+sourceIndex);
            LOGGER.info("ch idx = "+channelIndex);
            BIOFormatVSIHelper h = new BIOFormatVSIHelper(readerIdx, sourceIndex);
            VolatileVSIBdvSource<?, ?> vSrc = null;
            if (h.is24bitsRGB) {
                bdvSrc = new VSIBdvRGBSource(readerIdx, sourceIndex, channelIndex, switchZandC);
                vSrc = new VolatileVSIBdvSource<ARGBType, VolatileARGBType>(bdvSrc, new VolatileARGBType(), new SharedQueue(2));

            } else {
                if (h.is8bits)  {
                    bdvSrc = new VSIBdvUnsignedByteSource(readerIdx, sourceIndex, channelIndex, switchZandC);
                    vSrc = new VolatileVSIBdvSource<UnsignedByteType, VolatileUnsignedByteType>(bdvSrc, new VolatileUnsignedByteType(), new SharedQueue(2));
                }
                if (h.is16bits) {
                    bdvSrc = new VSIBdvUnsignedShortSource(readerIdx, sourceIndex, channelIndex, switchZandC);
                    vSrc = new VolatileVSIBdvSource<UnsignedShortType, VolatileUnsignedShortType>(bdvSrc, new VolatileUnsignedShortType(), new SharedQueue(2));
                }
            }

            if (vSrc==null) {
                LOGGER.severe("Couldn't display source type. UnsignedShort, UnsignedByte and 24 bit RGB only are supported. ");
                return;
            }

            LOGGER.info("name=" + omeMetaIdxOmeXml.getChannelName(sourceIndex, channelIndex));

            BdvOptions opts = BdvOptions.options();
            if (bdvSrc.numDimensions== 2) opts = opts.is2D();
            if (bdv_h != null) opts = opts.addTo(bdv_h);
            //{"Volatile","Standard", "Volatile + Standard"}
            BdvStackSource<?> bdvstack;
            switch (appendMode) {
                case "Volatile":
                    bdvstack = BdvFunctions.show(vSrc, opts);
                    break;
                case "Standard":
                    bdvstack = BdvFunctions.show(bdvSrc, opts);
                    break;
                //case "Volatile + Standard":
                //    bdvstack = BdvFunctions.show(vSrc, opts);
                //    bdvstack = BdvFunctions.show(bdvSrc, opts);
                //    break;
                default:
                    LOGGER.info("Invalid append mode: "+appendMode);
                    return;
            }

            if (!h.is24bitsRGB) {
                ome.xml.model.primitives.Color c = omeMetaIdxOmeXml.getChannelColor(sourceIndex, channelIndex);
                if (c!=null) {
                    bdvstack.setColor(new ARGBType(ARGBType.rgba(c.getRed(), c.getGreen(), c.getBlue(), 255)));
                } else {
                    if (omeMetaIdxOmeXml.getChannelEmissionWavelength(sourceIndex, channelIndex) != null) {
                        int emission = omeMetaIdxOmeXml.getChannelEmissionWavelength(sourceIndex, channelIndex).value(UNITS.NANOMETER).intValue();
                        LOGGER.info("emission=" + emission + " nm");
                        Color cAwt = getColorFromWavelength(emission);
                        bdvstack.setColor(new ARGBType(ARGBType.rgba(cAwt.getRed(), cAwt.getGreen(), cAwt.getBlue(), 255)));
                    }
                }
            }

            if ((!h.is24bitsRGB)&&(autoscale)) {
                // autoscale attempt based on min max of last pyramid -> no scaling of RGB image
                RandomAccessibleInterval<RealType> rai = bdvSrc.getSource(0,bdvSrc.getNumMipmapLevels()-1);
                RealType vMax = Util.getTypeFromInterval(rai);
                //RealType vMin = Util.getTypeFromInterval(rai);
                for (RealType px :  Views.flatIterable( rai ) ) {
                    //if (px.compareTo(vMin)<0) {
                    //    vMin.setReal(px.getRealDouble());
                    //}

                    if (px.compareTo(vMax)>0) {
                        vMax.setReal(px.getRealDouble());
                    }
                }
                //vMin.getRealDouble()
                // TODO understand why min do not work
                bdvstack.setDisplayRange(0, vMax.getRealDouble());
            }

            bdv_h = bdvstack.getBdvHandle();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    static private double Gamma = 0.80;
    static private double IntensityMax = 255;

    /** Taken from Earl F. Glynn's web page:
     * <a href="http://www.efg2.com/Lab/ScienceAndEngineering/Spectra.htm">Spectra Lab Report</a>
     * */
    public static int[] waveLengthToRGB(double Wavelength){
        double factor;
        double Red,Green,Blue;

        if((Wavelength >= 380) && (Wavelength<440)){
            Red = -(Wavelength - 440) / (440 - 380);
            Green = 0.0;
            Blue = 1.0;
        }else if((Wavelength >= 440) && (Wavelength<490)){
            Red = 0.0;
            Green = (Wavelength - 440) / (490 - 440);
            Blue = 1.0;
        }else if((Wavelength >= 490) && (Wavelength<510)){
            Red = 0.0;
            Green = 1.0;
            Blue = -(Wavelength - 510) / (510 - 490);
        }else if((Wavelength >= 510) && (Wavelength<580)){
            Red = (Wavelength - 510) / (580 - 510);
            Green = 1.0;
            Blue = 0.0;
        }else if((Wavelength >= 580) && (Wavelength<645)){
            Red = 1.0;
            Green = -(Wavelength - 645) / (645 - 580);
            Blue = 0.0;
        }else if((Wavelength >= 645) && (Wavelength<781)){
            Red = 1.0;
            Green = 0.0;
            Blue = 0.0;
        }else{
            Red = 0.0;
            Green = 0.0;
            Blue = 0.0;
        };

        // Let the intensity fall off near the vision limits

        if((Wavelength >= 380) && (Wavelength<420)){
            factor = 0.3 + 0.7*(Wavelength - 380) / (420 - 380);
        }else if((Wavelength >= 420) && (Wavelength<701)){
            factor = 1.0;
        }else if((Wavelength >= 701) && (Wavelength<781)){
            factor = 0.3 + 0.7*(780 - Wavelength) / (780 - 700);
        }else{
            factor = 0.0;
        };


        int[] rgb = new int[3];

        // Don't want 0^x = 1 for x <> 0
        rgb[0] = Red==0.0 ? 0 : (int) Math.round(IntensityMax * Math.pow(Red * factor, Gamma));
        rgb[1] = Green==0.0 ? 0 : (int) Math.round(IntensityMax * Math.pow(Green * factor, Gamma));
        rgb[2] = Blue==0.0 ? 0 : (int) Math.round(IntensityMax * Math.pow(Blue * factor, Gamma));

        return rgb;
    }


    public static Color getColorFromWavelength(int wv) {
        //https://stackoverflow.com/questions/1472514/convert-light-frequency-to-rgb
        int[] res = waveLengthToRGB(wv);
        return new Color(res[0], res[1], res[2]);
    }

}
