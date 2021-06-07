package ch.epfl.biop.bdv.command.exporter;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusSampler;
import ij.ImagePlus;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;

/**
 * Export sources as an ImagePlus object according to the orientation
 * of a bdv window.
 *
 * Many options are available for a full control of the way the export from bdv source to
 * a resliced ImagePlus can be performed.
 *
 * See {@link BasicBdvViewToImagePlusExportCommand} for a more simpler command with
 * reasonable estimated default values
 *
 * Stack is virtual and cached by default
 */

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Current BDV View To ImagePlus")
public class BdvViewToImagePlusExportCommand implements BdvPlaygroundActionCommand {

    private static final Logger logger = LoggerFactory.getLogger(BdvViewToImagePlusExportCommand.class);

    @Parameter(label = "BigDataViewer Frame")
    public BdvHandle bdv_h;

    @Parameter(label = "Capture Name")
    String capturename = "Capture_00";

    @Parameter(required = false)
    SourceAndConverter[] sacs;

    @Parameter(label="Match bdv frame window size", persist=false, callback = "matchXYBDVFrame")
    public boolean matchwindowsize =false;

    @Parameter(label = "Total Size X (physical unit)", callback = "matchXYBDVFrame")
    public double xsize = 100;

    @Parameter(label = "Total Size Y (physical unit)", callback = "matchXYBDVFrame")
    public double ysize = 100;

    @Parameter(label = "Half Thickness Z (above and below, physical unit, 0 for a single slice)")
    public double zsize = 100;

    @Parameter(label = "Start Timepoint (starts at 0)")
    public int timepointbegin = 0;

    @Parameter(label = "Number of timepoints", min = "1")
    public int numtimepoints = 1;

    @Parameter(label = "Time step", min = "1")
    public int timestep = 1;

    @Parameter(label = "XY Pixel size sampling (physical unit)", callback = "changePhysicalSampling")
    public double samplingxyinphysicalunit = 1;

    @Parameter(label = "Z Pixel size sampling (physical unit)", callback = "changePhysicalSampling")
    public double samplingzinphysicalunit = 1;

    @Parameter(label = "Interpolate")
    public boolean interpolate = true;

    @Parameter(label = "Cache the resampled image")
    public boolean cacheimage = true;

    @Parameter
    String unit="px";

    // Output imageplus window
    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus imageplus;

    @Override
    public void run() {

        // At least one source
        if ((sacs==null)||(sacs.length==0)) {
            logger.info("No selected source. Abort command.");
            return;
        }

        SourceAndConverter<?> model = createModelSource();

        imageplus = ImagePlusSampler.Builder()
                .cache(cacheimage)
                .unit(unit)
                .title(capturename)
                .setModel(model)
                .spaceSampling(samplingxyinphysicalunit, samplingxyinphysicalunit, samplingzinphysicalunit)
                .interpolate(interpolate)
                .timeRange(timepointbegin, numtimepoints)
                .timeSampling(timestep)
                .sources(sacs)
                .build().get();

        imageplus.show();
    }

    private SourceAndConverter<?> createModelSource() {
        // Origin is in fact the point 0,0,0 of the image
        // Get current big dataviewer transformation : source transform and viewer transform
        AffineTransform3D at3D = new AffineTransform3D(); // Empty Transform
        // viewer transform
        bdv_h.getViewerPanel().state().getViewerTransform(at3D); // Get current transformation by the viewer state and puts it into sourceToImgPlus
        //Center on the display center of the viewer ...
        double w = bdv_h.getViewerPanel().getDisplay().getWidth();
        double h = bdv_h.getViewerPanel().getDisplay().getHeight();
        // Center on the display center of the viewer ...
        at3D.translate(-w / 2, -h / 2, 0);
        // Getting an image independent of the view scaling unit (not sure)
        double xNorm = getNormTransform(0, at3D);//trans
        at3D.scale(1/xNorm);

        at3D.scale(1./ samplingxyinphysicalunit, 1./ samplingxyinphysicalunit, 1./ samplingzinphysicalunit);
        at3D.translate((xsize /(2* samplingxyinphysicalunit)), (ysize /(2* samplingxyinphysicalunit)), (zsize /(samplingzinphysicalunit)));

        long nPx = (long)(xsize / samplingxyinphysicalunit);
        long nPy = (long)(ysize / samplingxyinphysicalunit);
        long nPz;
        if (samplingzinphysicalunit ==0) {
            nPz = 1;
        } else {
            nPz = 1+(long)(zsize / (samplingzinphysicalunit /2.0)); // TODO : check div by 2
        }

        // At least a pixel in all directions
        if (nPz == 0) nPz = 1;
        if (nPx == 0) nPx = 1;
        if (nPy == 0) nPy = 1;

        return new EmptySourceAndConverterCreator(capturename, at3D.inverse(), nPx, nPy, nPz).get();
    }

    /**
     * Returns the norm of an axis after an affinetransform is applied
     * @param axis axis of the affine transform
     * @param t affine transform measured
     * @return the norm of an axis after an affinetransform is applied
     */
    static public double getNormTransform(int axis, AffineTransform3D t) {
        double f0 = t.get(axis,0);
        double f1 = t.get(axis,1);
        double f2 = t.get(axis,2);
        return Math.sqrt(f0 * f0 + f1 * f1 + f2 * f2);
    }

    /**
     * Returns the distance between two RealPoint pt1 and pt2
     * @param pt1 first point
     * @param pt2 second point
     * @return the distance between two RealPoint pt1 and pt2
     */
    static public double distance(RealPoint pt1, RealPoint pt2) {
        assert pt1.numDimensions()==pt2.numDimensions();
        double dsquared = 0;
        for (int i=0;i<pt1.numDimensions();i++) {
            double diff = pt1.getDoublePosition(i)-pt2.getDoublePosition(i);
            dsquared+=diff*diff;
        }
        return Math.sqrt(dsquared);
    }

    // -- Initializers --

    /**
     * Initializes xSize(Pix) and ySize(Pix) according to the current BigDataViewer window
     * Contrary to what intellij says, it IS used
     */
    public void matchXYBDVFrame() {
        if (matchwindowsize) {
            // Gets window size
            double w = bdv_h.getViewerPanel().getDisplay().getWidth();
            double h = bdv_h.getViewerPanel().getDisplay().getHeight();

            // Get global coordinates of the top left position  of the viewer
            RealPoint ptTopLeft = new RealPoint(3); // Number of dimension
            bdv_h.getViewerPanel().displayToGlobalCoordinates(0, 0, ptTopLeft);

            // Get global coordinates of the top right position  of the viewer
            RealPoint ptTopRight = new RealPoint(3); // Number of dimension
            bdv_h.getViewerPanel().displayToGlobalCoordinates(0, w, ptTopRight);

            // Get global coordinates of the top right position  of the viewer
            RealPoint ptBottomLeft = new RealPoint(3); // Number of dimension
            bdv_h.getViewerPanel().displayToGlobalCoordinates(h,0, ptBottomLeft);

            // Gets physical size of pixels based on window size, image sampling size and user requested pixel size
            this.xsize =distance(ptTopLeft, ptTopRight);
            this.ysize =distance(ptTopLeft, ptBottomLeft);
        }
    }

}
