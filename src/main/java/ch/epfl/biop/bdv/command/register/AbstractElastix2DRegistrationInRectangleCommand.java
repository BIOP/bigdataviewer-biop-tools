package ch.epfl.biop.bdv.command.register;

import bdv.viewer.SourceAndConverter;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;

abstract class AbstractElastix2DRegistrationInRectangleCommand extends SelectSourcesForRegistrationCommand {

    @Parameter(label = "ROI for registration (position x)")
    double px;

    @Parameter(label = "ROI for registration (position y)")
    double py;

    @Parameter(label = "ROI for registration (position z)")
    double pz;

    @Parameter(label = "ROI for registration (size x)")
    double sx;

    @Parameter(label = "ROI for registration (size y)")
    double sy;

    @Parameter(label = "Inspect registration result in ImageJ 1 windows (do not work with RGB images)")
    boolean showImagePlusRegistrationResult = false;

    @Parameter(label = "Number of iterations for each scale (default 100)")
    int maxIterationNumberPerScale = 100;

    @Parameter(type = ItemIO.OUTPUT)
    SourceAndConverter registeredSource;
}
