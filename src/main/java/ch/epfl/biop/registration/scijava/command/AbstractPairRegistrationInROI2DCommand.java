package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import net.imglib2.FinalRealInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.plugin.Parameter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

abstract public class AbstractPairRegistrationInROI2DCommand extends AbstractPairRegistration2DCommand {

    @Parameter(label = "ROI for registration (select custom in order to use the parameters below)", choices = {"intersection", "union", "custom"})
    String bounds = "intersection";

    @Parameter(label = "ROI for registration (position x)", style = "format:0.#####E0", required = false)
    double px;

    @Parameter(label = "ROI for registration (position y)", style = "format:0.#####E0", required = false)
    double py;

    @Parameter(label = "ROI for registration (size x)", style = "format:0.#####E0", required = false)
    double sx;

    @Parameter(label = "ROI for registration (size y)", style = "format:0.#####E0", required = false)
    double sy;

    final protected void addRegistrationParameters(Map<String, Object> parameters) {
        switch (bounds) {
            case "intersection": setIntersectionAsROI(); break;
            case "union": setUnionAsROI(); break;
            case "custom": break;
            default: System.err.println("Invalid bounds parameter, choose custom");
        }

        parameters.put(Registration.ROI_PX, px);
        parameters.put(Registration.ROI_PY, py);
        parameters.put(Registration.ROI_SX, sx);
        parameters.put(Registration.ROI_SY, sy);
        addRegistrationSpecificParametersExceptRoi(parameters);
    }

    abstract protected void addRegistrationSpecificParametersExceptRoi(Map<String, Object> parameters);

    void setIntersectionAsROI() {
        if (registration_pair == null) return;
        RealInterval boundMoving = getBoundingBox(registration_pair.getMovingSourcesRegistered());
        RealInterval boundFixed = getBoundingBox(registration_pair.getFixedSources());
        px = Math.max(boundMoving.realMin(0), boundFixed.realMin(0));
        py = Math.max(boundMoving.realMin(1), boundFixed.realMin(1));

        sx = Math.min(boundMoving.realMax(0), boundFixed.realMax(0))-px;
        sy = Math.min(boundMoving.realMax(1), boundFixed.realMax(1))-py;

    }

    void setUnionAsROI() {
        if (registration_pair == null) return;
        RealInterval boundMoving = getBoundingBox(registration_pair.getMovingSourcesRegistered());
        RealInterval boundFixed = getBoundingBox(registration_pair.getFixedSources());
        px = Math.min(boundMoving.realMin(0), boundFixed.realMin(0));
        py = Math.min(boundMoving.realMin(1), boundFixed.realMin(1));

        sx = Math.max(boundMoving.realMax(0), boundFixed.realMax(0))-px;
        sy = Math.max(boundMoving.realMax(1), boundFixed.realMax(1))-py;
        if ((sx<0)||(sy<0)) System.err.println("Warning, null intersection!");
    }

    static RealInterval getBoundingBox(SourceAndConverter<?>[] sources) {
        List<RealInterval> intervalList = Arrays.stream(sources).map((sourceAndConverter) -> {
            Interval interval = sourceAndConverter.getSpimSource().getSource(0, 0);
            AffineTransform3D sourceTransform = new AffineTransform3D();
            sourceAndConverter.getSpimSource().getSourceTransform(0, 0, sourceTransform);
            RealPoint corner0 = new RealPoint((float)interval.min(0), (float)interval.min(1), (float)interval.min(2));
            RealPoint corner1 = new RealPoint((float)interval.max(0), (float)interval.max(1), (float)interval.max(2));
            sourceTransform.apply(corner0, corner0);
            sourceTransform.apply(corner1, corner1);
            return new FinalRealInterval(new double[]{Math.min(corner0.getDoublePosition(0), corner1.getDoublePosition(0)), Math.min(corner0.getDoublePosition(1), corner1.getDoublePosition(1)), Math.min(corner0.getDoublePosition(2), corner1.getDoublePosition(2))}, new double[]{Math.max(corner0.getDoublePosition(0), corner1.getDoublePosition(0)), Math.max(corner0.getDoublePosition(1), corner1.getDoublePosition(1)), Math.max(corner0.getDoublePosition(2), corner1.getDoublePosition(2))});
        }).collect(Collectors.toList());
        RealInterval maxInterval = intervalList.stream()
                .reduce((i1, i2) ->
                        new FinalRealInterval(
                                new double[]{Math.min(i1.realMin(0), i2.realMin(0)), Math.min(i1.realMin(1), i2.realMin(1)), Math.min(i1.realMin(2), i2.realMin(2))},
                                new double[]{Math.max(i1.realMax(0), i2.realMax(0)), Math.max(i1.realMax(1), i2.realMax(1)), Math.max(i1.realMax(2), i2.realMax(2))}))
                .get();
        return maxInterval;
    }
}
