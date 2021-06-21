package ch.epfl.biop.bdv.command.exporter;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.sourceandconverter.exporter.ImagePlusSampler;
import ij.ImagePlus;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import org.scijava.ItemIO;
import org.scijava.ItemVisibility;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.fiji.bdvpg.scijava.ScijavaBdvDefaults;
import sc.fiji.bdvpg.scijava.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;

import java.util.*;

/**
 * This command is a simplified version of {@link BdvViewToImagePlusExportCommand} where
 * reasonable default settings are being set.
 *
 * The mipmap levels necessary for the sampling are estimated, all sources present in a bdv
 * window are exported, the size of the export is the window size, a composite window is created
 * by default.
 *
 * The freedom is still let free for interpolation, z
 *
 * Stack is virtual and cached by default
 * @param <T> non-volatile pixel type
 */

@Plugin(type = BdvPlaygroundActionCommand.class,
        menuPath = ScijavaBdvDefaults.RootMenu+"Sources>Export>Current BDV View To ImagePlus (Basic)")
public class BasicBdvViewToImagePlusExportCommand<T extends RealType<T>> implements BdvPlaygroundActionCommand {

    private static Logger logger = LoggerFactory.getLogger(BasicBdvViewToImagePlusExportCommand.class);

    @Parameter(label = "BigDataViewer Frame")
    public BdvHandle bdv_h;

    @Parameter(label = "Capture Name")
    String capturename = "Capture_00";

    @Parameter(label = "Half Thickness Z (above and below, physical unit, 0 for a single slice)")
    public double zsize = 100;

    @Parameter(label = "Output pixel size (physical unit)")
    public double samplingxyinphysicalunit = 1;

    @Parameter(label = "Z Pixel size sampling (physical unit)")
    public double samplingzinphysicalunit = 1;

    @Parameter(label = "Interpolate")
    public boolean interpolate = true;

    @Parameter( label = "Select Range", callback = "updateMessage", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String range = "You can use commas or colons to separate ranges. eg. '1:10' or '1,3,5,8' ";

    @Parameter( label = "Selected Timepoints. Leave blank for all", required = false )
    private String selected_timepoints_str = "";

    @Parameter( label = "Export mode", choices = {"Normal", "Virtual", "Virtual no-cache"}, required = false )
    private String export_mode = "Non virtual";

    //@Parameter( label = "Monitor loaded data")
    private Boolean monitor = true;

    @Parameter
    String unit="px";

    // Output imageplus window
    @Parameter(type = ItemIO.OUTPUT)
    public ImagePlus imageplus;

    double xSize, ySize;

    @Override
    public void run() {

        if (bdv_h.getViewerPanel().state().getSources().size()==0) {
            logger.info("No source present in Bdv. Abort command.");
            return;
        }

        List<SourceAndConverter<?>> sourceList = bdv_h.getViewerPanel().state().getSources();

        matchXYBDVFrame();

        SourceAndConverter<?> model = createModelSource();

        boolean cacheImage = false;
        boolean virtual = false;
        switch (export_mode) {
            case "Normal":
                virtual = false;
                break;
            case "Virtual":
                virtual = true;
                cacheImage = true;
                break;
            case "Virtual no-cache":
                virtual = true;
                cacheImage = false;
                break;
            default: throw new UnsupportedOperationException("Unrecognized export mode "+export_mode);
        }

        try {
            imageplus = ImagePlusSampler.Builder()
                    .cache(cacheImage)
                    .virtual(virtual)
                    .unit(unit)
                    .monitor(monitor)
                    .title(capturename)
                    .setModel(model)
                    .interpolate(interpolate)
                    .rangeT(selected_timepoints_str)
                    .sources(sourceList.toArray(new SourceAndConverter[0]))
                    .get();

        } catch (Exception e) {
            e.printStackTrace();
        }

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
        double xNorm = BdvViewToImagePlusExportCommand.getNormTransform(0, at3D);//trans
        at3D.scale(1/xNorm);

        at3D.scale(1./ samplingxyinphysicalunit, 1./ samplingxyinphysicalunit, 1./ samplingzinphysicalunit);
        at3D.translate((xSize/(2* samplingxyinphysicalunit)), (ySize/(2* samplingxyinphysicalunit)), (zsize /(samplingzinphysicalunit)));

        long nPx = (long)(xSize / samplingxyinphysicalunit);
        long nPy = (long)(ySize / samplingxyinphysicalunit);
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

    // -- Initializers --

    /**
     * Initializes xSize(Pix) and ySize(Pix) according to the current BigDataViewer window
     */
    public void matchXYBDVFrame() {
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
        this.xSize=BdvViewToImagePlusExportCommand.distance(ptTopLeft, ptTopRight);
        this.ySize=BdvViewToImagePlusExportCommand.distance(ptTopLeft, ptBottomLeft);
    }

}
