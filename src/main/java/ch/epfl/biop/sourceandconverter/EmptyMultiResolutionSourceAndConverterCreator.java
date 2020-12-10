package ch.epfl.biop.sourceandconverter;

import bdv.util.EmptyMultiresolutionSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterUtils;
import sc.fiji.bdvpg.sourceandconverter.importer.EmptySourceAndConverterCreator;

import java.util.function.Supplier;

public class EmptyMultiResolutionSourceAndConverterCreator implements Runnable, Supplier<SourceAndConverter> {
    EmptySourceAndConverterCreator c;


    AffineTransform3D at3D;

    long nx, ny, nz;

    int scalex, scaley, scalez;

    int numberOfResolutions;

    String name;

    /**
     * Simple constructor
     * @param name
     * @param at3D
     * @param nx
     * @param ny
     * @param nz
     */
    public EmptyMultiResolutionSourceAndConverterCreator(
            String name,
            AffineTransform3D at3D,
            long nx, long ny, long nz,
            int scalex, int scaley, int scalez,
            int numberOfResolutions
    ) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.at3D = at3D;
        this.name = name;
        this.scalex = scalex;
        this.scaley = scaley;
        this.scalez = scalez;
        this.numberOfResolutions = numberOfResolutions;
    }

    @Override
    public void run() {

    }

    @Override
    public SourceAndConverter get() {
        Source src = new EmptyMultiresolutionSource(nx,ny,nz,at3D,name, scalex, scaley, scalez, numberOfResolutions);

        SourceAndConverter sac;

        sac = SourceAndConverterUtils.createSourceAndConverter(src);

        return sac;
    }
}
