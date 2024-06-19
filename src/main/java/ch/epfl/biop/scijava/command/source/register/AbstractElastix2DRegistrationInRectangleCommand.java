package ch.epfl.biop.scijava.command.source.register;

import org.scijava.plugin.Parameter;

abstract class AbstractElastix2DRegistrationInRectangleCommand extends Abstract2DRegistrationInRectangleCommand {

    @Parameter(label = "Inspect registration result in ImageJ 1 windows (do not work with RGB images)")
    boolean showImagePlusRegistrationResult = false;

    @Parameter(label = "Number of iterations for each scale (default 100)")
    int maxIterationNumberPerScale = 100;

    @Parameter(label = "Minimal image size in pixel for initial downscaling (ignored if the original image is too small)")
    int minPixSize = 32;

    @Parameter(label = "Starts by aligning gravity centers")
    boolean automaticTransformInitialization = false;

    @Parameter
    boolean verbose = false;

    @Parameter(label = "Background offset value for moving image")
    double background_offset_value_moving = 0;

    @Parameter(label = "Background offset value for fixed image")
    double background_offset_value_fixed = 0;

}
