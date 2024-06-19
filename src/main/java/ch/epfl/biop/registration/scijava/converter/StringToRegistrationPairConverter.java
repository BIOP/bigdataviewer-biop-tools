package ch.epfl.biop.registration.scijava.converter;

import ch.epfl.biop.registration.RegistrationPair;
import org.scijava.convert.AbstractConverter;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.util.Optional;


@SuppressWarnings("unused")
@Plugin(type = org.scijava.convert.Converter.class)
public class StringToRegistrationPairConverter<I extends String, O extends RegistrationPair> extends
        AbstractConverter<I, O>
{

    @Parameter
    ObjectService os;

    @Override
    public <T> T convert(Object src, Class<T> dest) {
        Optional<RegistrationPair> ans = os.getObjects(RegistrationPair.class).stream().filter(
                rp -> (rp.getName().equals(src))).findFirst();
        return (T) ans.orElse(null);
    }

    @Override
    public Class<O> getOutputType() {
        return (Class<O>) RegistrationPair.class;
    }

    @Override
    public Class<I> getInputType() {
        return (Class<I>) String.class;
    }
}
