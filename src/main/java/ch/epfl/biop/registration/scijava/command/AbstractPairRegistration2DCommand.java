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

abstract public class AbstractPairRegistration2DCommand implements Command {

    @Parameter
    Context ctx;

    @Parameter
    RegistrationPair registration_pair;

    @Parameter(type = ItemIO.OUTPUT)
    boolean success;

    @Override
    final public void run() {
        synchronized (registration_pair) {
            try {
                SourceAndConverter<?>[] moving_sources = registration_pair.getMovingSourcesRegistered();
                SourceAndConverter<?>[] fixed_sources = registration_pair.getFixedSources();

                Registration<SourceAndConverter<?>[]> registration = getRegistration();
                registration.setScijavaContext(ctx);

                registration.setTimePoint(0);

                registration.setMovingImage(getSourcesProcessorMoving().apply(moving_sources));
                registration.setFixedImage(getSourcesProcessorFixed().apply(fixed_sources));

                Map<String, Object> parameters = new HashMap<>();

                addRegistrationParameters(parameters);

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
            } catch (Exception e) {
                System.err.println("Error during registration: "+e.getMessage());
                e.printStackTrace();
                success = false;
            }
        }

    }

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

    protected abstract void addRegistrationParameters(Map<String, Object> parameters);

    abstract Registration<SourceAndConverter<?>[]> getRegistration();

    abstract protected SourcesProcessor getSourcesProcessorFixed();

    abstract protected SourcesProcessor getSourcesProcessorMoving();

    protected abstract boolean validate();

    public static Map<String,String> convertToString(Context ctx, Map<String, Object> params) {
        Map<String,String> convertedParams = new HashMap<>();

        ConvertService cs = ctx.getService(ConvertService.class);

        params.keySet().forEach(k -> convertedParams.put(k, cs.convert(params.get(k), String.class)));

        return convertedParams;
    }

}
