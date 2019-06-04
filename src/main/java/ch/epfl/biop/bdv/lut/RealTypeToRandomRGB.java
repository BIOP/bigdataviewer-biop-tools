package ch.epfl.biop.bdv.lut;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public class RealTypeToRandomRGB<T> implements Converter<RealType, ARGBType> {

    long seed;

    public RealTypeToRandomRGB(long seed) {
        this.seed=seed;
    }

    public RealTypeToRandomRGB() {
        this(System.currentTimeMillis());
    }

    @Override
    public void convert(RealType input, ARGBType output) {
        output.set((int) (input.hashCode()*seed) % (256*256*256));
    }

}
