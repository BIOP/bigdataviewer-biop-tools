package bdv.util;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.*;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class EmptyMultiresolutionSource implements Source<UnsignedShortType>, Serializable {

    EmptyMultiresolutionSource.EmptyMultiresolutionSourceParams params;

    transient protected final DefaultInterpolators< UnsignedShortType > interpolators = new DefaultInterpolators<>();

    transient final List<RandomAccessibleInterval> raiList;
    transient final List<AffineTransform3D> transformList;

    public EmptyMultiresolutionSource(long nx, long ny, long nz, AffineTransform3D at3D, String name,
                                      int scalex, int scaley, int scalez, int numberOfResolutions) {

        params = new EmptyMultiresolutionSource.EmptyMultiresolutionSourceParams();
        params.nx = nx;
        params.ny = ny;
        params.nz = nz;
        params.scalex = scalex;
        params.scaley = scaley;
        params.scalez = scalez;

        BiConsumer<Localizable, UnsignedShortType > fun = (l, t) -> t.set(10);

        RandomAccessible ra = new FunctionRandomAccessible<>(3,
                fun, UnsignedShortType::new);

        if (numberOfResolutions<=0) {
            System.err.println("Number of resolution issue, this value cannot be below 1");
            numberOfResolutions = 1;
        }
        params.numberOfResolutions = numberOfResolutions;

        raiList = new ArrayList<>(numberOfResolutions);
        double currentNX = nx;
        double currentNY = ny;
        double currentNZ = nz;

        transformList = new ArrayList<>();

        for (int i=0;i<numberOfResolutions;i++) {
            AffineTransform3D m = new AffineTransform3D();
            m.set(at3D);
            m.scale( (double)nx/(long)currentNX, (double)ny/(long)currentNY, (double)nz/(long)currentNZ );
            transformList.add(m);
            raiList.add(Views.interval(ra, new FinalInterval((long) currentNX,(long) currentNY, (long) currentNZ)));
            currentNX=currentNX/scalex;
            currentNY=currentNY/scaley;
            currentNZ=currentNZ/scalez;
            if (currentNX<1) currentNX = 1;
            if (currentNY<1) currentNY = 1;
            if (currentNZ<1) currentNZ = 1;
        }

        params.at3D = at3D;
        params.name = name;
    }

    @Override
    public boolean isPresent(int t) {
        return true;
    }

    @Override
    public RandomAccessibleInterval<UnsignedShortType> getSource(int t, int level) {
        return raiList.get(level);
    }

    @Override
    public RealRandomAccessible<UnsignedShortType> getInterpolatedSource(int t, int level, Interpolation method) {
        ExtendedRandomAccessibleInterval<UnsignedShortType, RandomAccessibleInterval< UnsignedShortType >>
                eView = Views.extendZero(getSource( t, level ));
        RealRandomAccessible< UnsignedShortType > realRandomAccessible = Views.interpolate( eView, interpolators.get(method) );
        return realRandomAccessible;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        transform.set(transformList.get(level));
    }

    @Override
    public UnsignedShortType getType() {
        return new UnsignedShortType();
    }

    @Override
    public String getName() {
        return params.name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return new VoxelDimensions() {
            @Override
            public String unit() {
                return "undefined";
            }

            @Override
            public void dimensions(double[] dimensions) {
                dimensions[0] = 1;
                dimensions[1] = 1;
                dimensions[2] = 1;
            }

            @Override
            public double dimension(int d) {
                return 1;
            }

            @Override
            public int numDimensions() {
                return 3;
            }
        };
    }

    @Override
    public int getNumMipmapLevels() {
        return params.numberOfResolutions;
    }

    static public class EmptyMultiresolutionSourceParams implements Cloneable, Serializable {
        public long nx,ny,nz;
        public AffineTransform3D at3D;
        public String name;
        public int scalex, scaley, scalez, numberOfResolutions;

        public EmptyMultiresolutionSourceParams() {
            nx = 1;
            ny = 1;
            nz = 1;
            at3D = new AffineTransform3D();
            name = "";
            scalex = 1;
            scaley = 1;
            scalez = 1;
            numberOfResolutions = 1;
        }

        public EmptyMultiresolutionSourceParams(EmptyMultiresolutionSource.EmptyMultiresolutionSourceParams p) {
            nx = p.nx;
            ny = p.ny;
            nz = p.nz;
            at3D = new AffineTransform3D();
            at3D.set(p.at3D);
            name = p.name;
            scalex = p.scalex;
            scaley = p.scaley;
            scalez = p.scalez;
            numberOfResolutions = p.numberOfResolutions;
        }
    }
}
