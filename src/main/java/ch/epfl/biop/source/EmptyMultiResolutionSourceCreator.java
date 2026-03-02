package ch.epfl.biop.source;

import bdv.util.EmptyMultiresolutionSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import sc.fiji.bdvpg.source.SourceHelper;
import sc.fiji.bdvpg.source.importer.EmptySourceCreator;

import java.util.function.Supplier;

public class EmptyMultiResolutionSourceCreator implements Runnable, Supplier<SourceAndConverter<?>> {

    AffineTransform3D at3D;

    long nx, ny, nz, nt;

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
     * @param nt number of timepoints
     * @param scalex downscaling factor in x between resolution levels
     * @param scaley downscaling factor in y between resolution levels
     * @param scalez downscaling factor in z between resolution levels
     * @param numberOfResolutions number of resolution levels to generate
     */
    public EmptyMultiResolutionSourceCreator(
            String name,
            AffineTransform3D at3D,
            long nx, long ny, long nz, long nt,
            int scalex, int scaley, int scalez,
            int numberOfResolutions
    ) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        this.nt = nt;
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
    public SourceAndConverter<?> get() {
        Source<UnsignedShortType> src = new EmptyMultiresolutionSource(nx,ny,nz,nt,at3D,name, scalex, scaley, scalez, numberOfResolutions);

        SourceAndConverter<?> source;

        source = SourceHelper.createSourceAndConverter(src);

        return source;
    }
}
