package ch.epfl.biop.bdv.command.register;

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;

abstract class AbstractElastix2DRegistrationInRectangleCommand extends SelectSourcesForRegistrationCommand {

    @Parameter(label = "ROI for registration (position x)", style = "format:0.#####E0")
    double px;

    @Parameter(label = "ROI for registration (position y)", style = "format:0.#####E0")
    double py;

    @Parameter(label = "ROI for registration (position z)", style = "format:0.#####E0")
    double pz;

    @Parameter(label = "ROI for registration (size x)", style = "format:0.#####E0")
    double sx;

    @Parameter(label = "ROI for registration (size y)", style = "format:0.#####E0")
    double sy;

    @Parameter(label = "Inspect registration result in ImageJ 1 windows (do not work with RGB images)")
    boolean showImagePlusRegistrationResult = false;

    @Parameter(label = "Number of iterations for each scale (default 100)")
    int maxIterationNumberPerScale = 100;

    @Parameter(label = "Minimal image size in pixel for initial downscaling (ignored if the original image is too small)")
    int minPixSize = 32;

    @Parameter(label = "Starts by aligning gravity centers")
    boolean automaticTransformInitialization = false;

    @Parameter(type = ItemIO.OUTPUT)
    AffineTransform3D at3D;

    @Parameter
    boolean verbose = false;

    @Parameter(label = "Background offset value for moving image")
    double background_offset_value_moving = 0;

    @Parameter(label = "Background offset value for fixed image")
    double background_offset_value_fixed = 0;

}
