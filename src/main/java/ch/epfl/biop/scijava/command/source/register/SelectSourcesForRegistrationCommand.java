package ch.epfl.biop.scijava.command.source.register;

import bdv.viewer.SourceAndConverter;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;

abstract public class SelectSourcesForRegistrationCommand implements Command {

    @Parameter(label = "Fixed source for registration", description = "fixed source")
    SourceAndConverter<?>[] sacs_fixed;

    @Parameter(label = "Timepoint of the fixed source")
    int tpFixed;

    @Parameter(label = "Resolution level of the fixed source (0 = highest)")
    int levelFixedSource;

    @Parameter(label = "Moving source for registration", description = "moving source")
    SourceAndConverter[] sacs_moving;

    @Parameter(label = "Timepoint of the moving source")
    int tpMoving;

    @Parameter(label = "Resolution level of the moving source (0 = highest)")
    int levelMovingSource;

    @Parameter(label = "Pixel size in physical unit used for image resampling")
    double pxSizeInCurrentUnit;

    @Parameter(label = "Interpolate when resampling images")
    boolean interpolate;

}
