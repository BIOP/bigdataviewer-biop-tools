package ch.epfl.biop.bdv.lut;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class NewConv implements Converter<RealType, UnsignedShortType> {



    @Override
    public void convert(RealType input, UnsignedShortType output) {
        output.set((int) input.getRealDouble()*2);
    }
}
