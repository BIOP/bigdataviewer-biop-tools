package ch.epfl.biop.sourceandconverter;

import bdv.util.EmptyMultiresolutionSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
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
     * @param name name
     * @param at3D affine transform of the source
     * @param nx number of voxels in x
     * @param ny number of voxels in y
     * @param nz number of voxels in z
     * @param scalex downscaling factor in x between resolution levels
     * @param scaley downscaling factor in y between resolution levels
     * @param scalez downscaling factor in z between resolution levels
     * @param numberOfResolutions number of resolution levels to generate
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

        sac = SourceAndConverterHelper.createSourceAndConverter(src);

        return sac;
    }
}
