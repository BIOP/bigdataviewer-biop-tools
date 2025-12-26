package ch.epfl.biop.scijava.command.source.register;

import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ItemIO;
import org.scijava.plugin.Parameter;

abstract class Abstract2DRegistrationInRectangleCommand extends SelectSourcesForRegistrationCommand {

    @Parameter(label = "ROI Position X",
            style = "format:0.#####E0",
            description = "X coordinate of the registration region top-left corner")
    double px;

    @Parameter(label = "ROI Position Y",
            style = "format:0.#####E0",
            description = "Y coordinate of the registration region top-left corner")
    double py;

    @Parameter(label = "ROI Position Z",
            style = "format:0.#####E0",
            description = "Z coordinate of the registration plane")
    double pz;

    @Parameter(label = "ROI Size X",
            style = "format:0.#####E0",
            description = "Width of the registration region")
    double sx;

    @Parameter(label = "ROI Size Y",
            style = "format:0.#####E0",
            description = "Height of the registration region")
    double sy;

    @Parameter(type = ItemIO.OUTPUT,
            description = "The computed affine transformation to align moving to fixed")
    AffineTransform3D at3d;

}
