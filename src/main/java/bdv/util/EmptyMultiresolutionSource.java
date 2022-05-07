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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class EmptyMultiresolutionSource implements Source<UnsignedShortType>, Serializable {

    private static Logger logger = LoggerFactory.getLogger(EmptyMultiresolutionSource.class);

    EmptyMultiresolutionSource.EmptyMultiresolutionSourceParams params;

    transient protected final DefaultInterpolators< UnsignedShortType > interpolators = new DefaultInterpolators<>();

    transient final List<RandomAccessibleInterval> raiList;
    transient final List<AffineTransform3D> transformList;

    public EmptyMultiresolutionSource(long nx, long ny, long nz, long numberOfTimePoints, AffineTransform3D at3D, String name,
                                      int scalex, int scaley, int scalez, int numberOfResolutions) {

        params = new EmptyMultiresolutionSource.EmptyMultiresolutionSourceParams();
        params.nx = nx;
        params.ny = ny;
        params.nz = nz;
        params.numberOfTimepoints = numberOfTimePoints;
        params.scalex = scalex;
        params.scaley = scaley;
        params.scalez = scalez;

        BiConsumer<Localizable, UnsignedShortType > fun = (l, t) -> t.set(10);

        RandomAccessible ra = new FunctionRandomAccessible<>(3,
                fun, UnsignedShortType::new);

        if (numberOfResolutions<=0) {
            logger.warn("Number of resolution issue, this value cannot be below 1, value ("+numberOfResolutions+") overriden to 1");
            numberOfResolutions = 1;
        }
        params.numberOfResolutions = numberOfResolutions;

        raiList = new ArrayList<>(numberOfResolutions);
        double currentNX = nx;
        double currentNY = ny;
        double currentNZ = nz;

        transformList = new ArrayList<>();

        /*
        double[] m = coord.getRowPackedCopy();
        double[] voxelSizes = new double[3];
        for(int d = 0; d < 3; ++d) {
            voxelSizes[d] = Math.sqrt(m[d] * m[d] + m[d + 4] * m[d + 4] + m[d + 8] * m[d + 8]);
        }
         */
        for (int i=0;i<numberOfResolutions;i++) {
            AffineTransform3D m = new AffineTransform3D();
            m.set(at3D);
            double[] mat = m.getRowPackedCopy();
            double sX = (double)nx/(long)currentNX;
            double sY = (double)ny/(long)currentNY;
            double sZ = (double)nz/(long)currentNZ;
            mat[0]*=sX;mat[4]*=sX;mat[8]*=sX;
            mat[1]*=sY;mat[5]*=sY;mat[9]*=sY;
            mat[2]*=sZ;mat[6]*=sZ;mat[10]*=sZ;
            m.set(mat);
            // this doesn't look good
            // m.scale( (double)nx/(long)currentNX, (double)ny/(long)currentNY, (double)nz/(long)currentNZ );
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
        public long nx,ny,nz, numberOfTimepoints;
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
            numberOfTimepoints = 1;
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
            numberOfTimepoints = p.numberOfTimepoints;
        }
    }
}
