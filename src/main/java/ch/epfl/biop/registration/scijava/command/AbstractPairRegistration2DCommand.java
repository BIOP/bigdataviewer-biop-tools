package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.RegistrationPair;
import ch.epfl.biop.sourceandconverter.processor.SourcesChannelsSelect;
import org.scijava.Context;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.convert.ConvertService;
import org.scijava.plugin.Parameter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

abstract public class AbstractPairRegistration2DCommand implements Command {

    @Parameter
    Context ctx;

    @Parameter
    RegistrationPair registration_pair;

    @Parameter(label = "Fixed image channels used for registration (comma separated)")
    String channels_fixed_csv;

    @Parameter(label = "Moving image channels used for registration (comma separated)")
    String channels_moving_csv;

    @Parameter(label = "ROI for registration (position x)", style = "format:0.#####E0")
    double px;

    @Parameter(label = "ROI for registration (position y)", style = "format:0.#####E0")
    double py;

    @Parameter(label = "ROI for registration (size x)", style = "format:0.#####E0")
    double sx;

    @Parameter(label = "ROI for registration (size y)", style = "format:0.#####E0")
    double sy;

    @Parameter(type = ItemIO.OUTPUT)
    boolean success;

    @Override
    public void run() {
        synchronized (registration_pair) {
            SourceAndConverter<?>[] moving_sources = registration_pair.getMovingSourcesRegistered();
            SourceAndConverter<?>[] fixed_sources = registration_pair.getFixedSources();

            List<Integer> moving_channels;
            List<Integer> fixed_channels;

            try {
                moving_channels = Arrays.stream(channels_moving_csv.split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
                fixed_channels = Arrays.stream(channels_fixed_csv.split(","))
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
            } catch (NumberFormatException e) {
                System.err.println("Number parsing exception " + e.getMessage());
                return;
            }

            if (moving_channels.isEmpty()) {
                System.err.println("Error, you did not specify any channel within the moving image.");
                return;
            }

            if (fixed_channels.isEmpty()) {
                System.err.println("Error, you did not specify any channel within the fixed image.");
                return;
            }

            int maxIndexMoving = Collections.max(moving_channels);
            int minIndexMoving = Collections.min(moving_channels);

            int maxIndexFixed = Collections.max(fixed_channels);
            int minIndexFixed = Collections.min(fixed_channels);

            if ((minIndexMoving < 0) || (minIndexFixed < 0)) {
                System.err.println("All channels indices should be positive");
                return;
            }

            if (!(maxIndexFixed < fixed_sources.length)) {
                System.err.println("The max index within the fixed sources (" + maxIndexFixed + ") is above its maximum (" + (fixed_sources.length - 1) + ")");
                return;
            }

            if (!(maxIndexMoving < moving_sources.length)) {
                System.err.println("The max index within the moving sources (" + maxIndexFixed + ") is above its maximum (" + (moving_sources.length - 1) + ")");
                return;
            }

            Registration<SourceAndConverter<?>[]> registration = getRegistration();
            registration.setScijavaContext(ctx);

            registration.setTimePoint(0);

            registration.setMovingImage(new SourcesChannelsSelect(moving_channels).apply(moving_sources));
            registration.setFixedImage(new SourcesChannelsSelect(fixed_channels).apply(fixed_sources));

            Map<String, Object> parameters = new HashMap<>();

            parameters.put(Registration.ROI_PX, px);
            parameters.put(Registration.ROI_PY, py);
            parameters.put(Registration.ROI_SX, sx);
            parameters.put(Registration.ROI_SY, sy);

            addRegistrationSpecificParameters(parameters);

            registration.setRegistrationParameters(convertToString(ctx, parameters));

            boolean ok = validate();

            if (!ok) {
                System.err.println("Validation failed.");
                return;
            }

            success = registration.register(); // Do it!

            if (success) {
                //registered_sources = registration.getTransformedImageMovingToFixed(moving_sources);
                registration_pair.appendRegistration(registration);
            } else {
                System.err.println("Registration unsuccessful: " + registration.getExceptionMessage());
            }
        }

    }

    protected abstract void addRegistrationSpecificParameters(Map<String, Object> parameters);

    abstract Registration<SourceAndConverter<?>[]> getRegistration();

    protected abstract boolean validate();

    public static Map<String,String> convertToString(Context ctx, Map<String, Object> params) {
        Map<String,String> convertedParams = new HashMap<>();

        ConvertService cs = ctx.getService(ConvertService.class);

        params.keySet().forEach(k -> convertedParams.put(k, cs.convert(params.get(k), String.class)));

        return convertedParams;
    }
}
