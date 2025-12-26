package ch.epfl.biop.scijava.command.source.register;

import org.scijava.plugin.Parameter;

abstract class AbstractElastix2DRegistrationInRectangleCommand extends Abstract2DRegistrationInRectangleCommand {

    @Parameter(label = "Show Registration",
            description = "When checked, displays registration results in ImageJ windows (not compatible with RGB images)")
    boolean show_image_registration = false;

    @Parameter(label = "Iterations Per Scale",
            description = "Maximum number of optimization iterations at each pyramid level")
    int max_iteration_per_scale = 100;

    @Parameter(label = "Minimum Image Size",
            description = "Minimum image size in pixels for the coarsest pyramid level")
    int min_image_size_pix = 32;

    @Parameter(label = "Auto Center",
            description = "When checked, initially aligns images by their centers of gravity")
    boolean automatic_transform_initialization = false;

    @Parameter(label = "Verbose",
            description = "When checked, outputs detailed registration progress information")
    boolean verbose = false;

    @Parameter(label = "Moving Background Offset",
            description = "Value to add to moving image background for better matching")
    double background_offset_value_moving = 0;

    @Parameter(label = "Fixed Background Offset",
            description = "Value to add to fixed image background for better matching")
    double background_offset_value_fixed = 0;

}
