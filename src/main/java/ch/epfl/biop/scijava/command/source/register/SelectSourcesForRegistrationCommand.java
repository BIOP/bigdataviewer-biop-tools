package ch.epfl.biop.scijava.command.source.register;

import bdv.viewer.SourceAndConverter;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;

abstract public class SelectSourcesForRegistrationCommand implements Command {

    @Parameter(label = "Fixed Source(s)",
            description = "Reference source(s) that remain stationary during registration")
    SourceAndConverter<?>[] sacs_fixed;

    @Parameter(label = "Fixed Timepoint",
            description = "Timepoint of the fixed source to use for registration")
    int tp_fixed;

    @Parameter(label = "Fixed Resolution Level",
            description = "Resolution level of the fixed source (0 = highest resolution)")
    int level_fixed_source;

    @Parameter(label = "Moving Source(s)",
            description = "Source(s) to be aligned to the fixed reference")
    SourceAndConverter[] sacs_moving;

    @Parameter(label = "Moving Timepoint",
            description = "Timepoint of the moving source to use for registration")
    int tp_moving;

    @Parameter(label = "Moving Resolution Level",
            description = "Resolution level of the moving source (0 = highest resolution)")
    int level_moving_source;

    @Parameter(label = "Resampling Pixel Size",
            description = "Pixel size in world coordinates units used when resampling images for registration")
    double px_size_in_current_unit;

    @Parameter(label = "Interpolate",
            description = "When checked, uses interpolation when resampling images")
    boolean interpolate;

}
