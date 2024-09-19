package ch.epfl.biop.scijava.command.source.register;

import bdv.viewer.SourceAndConverter;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;

abstract public class SelectSourcesForRegistrationCommand implements Command {

    @Parameter(label = "Fixed source for registration", description = "fixed source")
    SourceAndConverter<?>[] sacs_fixed;

    @Parameter(label = "Timepoint of the fixed source")
    int tp_fixed;

    @Parameter(label = "Resolution level of the fixed source (0 = highest)")
    int level_fixed_source;

    @Parameter(label = "Moving source for registration", description = "moving source")
    SourceAndConverter[] sacs_moving;

    @Parameter(label = "Timepoint of the moving source")
    int tp_moving;

    @Parameter(label = "Resolution level of the moving source (0 = highest)")
    int level_moving_source;

    @Parameter(label = "Pixel size in physical unit used for image resampling")
    double px_size_in_current_unit;

    @Parameter(label = "Interpolate when resampling images")
    boolean interpolate;

}
