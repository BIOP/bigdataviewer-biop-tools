package ch.epfl.biop.scijava.command.source.register;

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;

abstract class Abstract2DRegistrationInRectangleCommand extends SelectSourcesForRegistrationCommand {

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

    @Parameter(type = ItemIO.OUTPUT)
    AffineTransform3D at3D;

}
