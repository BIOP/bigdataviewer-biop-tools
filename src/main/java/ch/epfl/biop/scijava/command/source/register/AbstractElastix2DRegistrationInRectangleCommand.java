package ch.epfl.biop.scijava.command.source.register;

import org.scijava.plugin.Parameter;

abstract class AbstractElastix2DRegistrationInRectangleCommand extends Abstract2DRegistrationInRectangleCommand {

    @Parameter(label = "Inspect registration result in ImageJ 1 windows (do not work with RGB images)")
    boolean show_image_registration = false;

    @Parameter(label = "Number of iterations for each scale (default 100)")
    int max_iteration_per_scale = 100;

    @Parameter(label = "Minimal image size in pixel for initial downscaling (ignored if the original image is too small)")
    int min_image_size_pix = 32;

    @Parameter(label = "Starts by aligning gravity centers")
    boolean automatic_transform_initialization = false;

    @Parameter
    boolean verbose = false;

    @Parameter(label = "Background offset value for moving image")
    double background_offset_value_moving = 0;

    @Parameter(label = "Background offset value for fixed image")
    double background_offset_value_fixed = 0;

}
