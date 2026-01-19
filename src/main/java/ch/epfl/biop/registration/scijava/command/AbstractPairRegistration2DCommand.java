package ch.epfl.biop.registration.scijava.command;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.registration.Registration;
import ch.epfl.biop.registration.RegistrationPair;
import ch.epfl.biop.sourceandconverter.processor.SourcesChannelsSelect;
import ch.epfl.biop.sourceandconverter.processor.SourcesProcessor;
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

/**
 * Abstract base class for 2D registration commands that operate on registration pairs.
 * Provides the common framework for executing pair-based registrations.
 */
abstract public class AbstractPairRegistration2DCommand implements Command {

    @Parameter
    Context ctx;

    @Parameter(label = "Registration Pair",
            description = "The registration pair to apply the registration to")
    RegistrationPair registration_pair;

    @Parameter(type = ItemIO.OUTPUT,
            label = "Success",
            description = "True if the registration completed successfully")
    boolean success = false;

    @Override
    final public void run() {
        synchronized (registration_pair) {
            try {
                Map<String, Object> parameters = new HashMap<>();
                Registration<SourceAndConverter<?>[]> registration = getRegistration();
                registration.setScijavaContext(ctx);
                registration.setTimePoint(0);
                addRegistrationParameters(parameters);

                boolean ok = validate();

                if (!ok) {
                    System.err.println("Validation failed.");
                    return;
                }

                success = registration_pair
                        .executeRegistration(registration,
                                convertToString(ctx, parameters),
                                getSourcesProcessorFixed(),
                                getSourcesProcessorMoving());

                if (!success) {
                    System.err.println("Registration unsuccessful: " + registration_pair.getLastErrorMessage());
                }
            } catch (Exception e) {
                System.err.println("Error during registration: "+e.getMessage());
                e.printStackTrace();
                success = false;
            }
        }

    }

    /**
     * Adds registration-specific parameters to the provided map.
     *
     * @param parameters the map to populate with registration parameters
     */
    abstract protected void addRegistrationParameters(Map<String, Object> parameters);

    /**
     * Creates and returns the registration instance to be used.
     *
     * @return the registration object
     */
    abstract Registration<SourceAndConverter<?>[]> getRegistration();

    /**
     * Returns the sources processor for the fixed image.
     *
     * @return the processor for fixed sources
     */
    abstract protected SourcesProcessor getSourcesProcessorFixed();

    /**
     * Returns the sources processor for the moving image.
     *
     * @return the processor for moving sources
     */
    abstract protected SourcesProcessor getSourcesProcessorMoving();

    /**
     * Validates the registration parameters and configuration.
     *
     * @return true if validation succeeds, false otherwise
     */
    abstract protected boolean validate();

    /**
     * Converts a map of objects to a map of strings using the SciJava convert service.
     *
     * @param ctx the SciJava context
     * @param params the map of parameters to convert
     * @return a map with string values
     */
    public static Map<String,String> convertToString(Context ctx, Map<String, Object> params) {
        Map<String,String> convertedParams = new HashMap<>();

        ConvertService cs = ctx.getService(ConvertService.class);

        params.keySet().forEach(k -> convertedParams.put(k, cs.convert(params.get(k), String.class)));

        return convertedParams;
    }

    /**
     * Creates a channel selector from a CSV string of channel indices.
     *
     * @param channelsCsv comma-separated list of channel indices
     * @param nChannels total number of available channels
     * @return a SourcesChannelsSelect processor
     * @throws NumberFormatException if the CSV contains non-numeric values
     * @throws IndexOutOfBoundsException if channel indices are out of range
     */
    protected static SourcesChannelsSelect getChannelProcessorFromCsv(String channelsCsv, int nChannels) throws NumberFormatException, IndexOutOfBoundsException {
        List<Integer> channels = Arrays.stream(channelsCsv.split(","))
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        int maxIndex = Collections.max(channels);
        int minIndex = Collections.min(channels);

        if (minIndex < 0) {
            System.err.println("All channels indices should be positive");
            throw new IndexOutOfBoundsException();
        }

        if (!(maxIndex < nChannels)) {
            System.err.println("The max index (" + maxIndex + ") is above its maximum (" + (nChannels) + ")");
            throw new IndexOutOfBoundsException();
        }

        return new SourcesChannelsSelect(channels);
    }

}
