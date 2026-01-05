package ch.epfl.biop.registration.plugin;

import ij.ImagePlus;
import net.imglib2.realtransform.InvertibleRealTransform;

import java.util.Map;

@SuppressWarnings("SameReturnValue")
public interface SimpleRegistrationPlugin {

    /**
     * @return Sampling required for the registration, in micrometer
     */
    double getVoxelSizeInMicron();

    /**
     * Is called before registration to pass any extra registration parameter
     * argument. Passed as a dictionary of String to preserve serialization
     * capability.
     * @param parameters dictionary of parameters
     */
    void setRegistrationParameters(Map<String,String> parameters);

    /**
     *
     * @param fixed image
     * @param moving image
     * @param fixedMask optional mask for the fixed image (can be null)
     * @param movingMask optional mask for the moving image (can be null)
     * @return the transform, result of the registration, in
     * going from fixed to moving coordinates, in pixels
     */
    InvertibleRealTransform register(ImagePlus fixed, ImagePlus moving, ImagePlus fixedMask, ImagePlus movingMask);
}
