package ch.epfl.biop.bdv.sampleimage;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class RecursiveFIJI implements Source<UnsignedShortType> {
    public static short[][] fijiData =
            {{1,1,1,1,1,1,1},
             {1,0,0,0,0,0,0},
             {1,0,1,0,1,0,1},
             {1,0,1,0,1,0,1},
             {1,0,1,0,1,0,1},
             {1,0,0,0,1,0,0},
             {1,0,1,1,1,0,0}};

    int nLevels=0;
    VoxelDimensions vd;

    public RecursiveFIJI(int nLevels) {
        this.nLevels = nLevels;
        vd = new VoxelDimensions() {
            @Override
            public String unit() {
                return "px";
            }

            @Override
            public void dimensions(double[] dimensions) {
                dimensions[0] = 1;
                dimensions[1] = 1;
            }

            @Override
            public double dimension(int d) {
                return 1;
            }

            @Override
            public int numDimensions() {
                return 2;
            }
        };
    }

    @Override
    public boolean isPresent(int t) {
        return true;
    }

    @Override
    public RandomAccessibleInterval<UnsignedShortType> getSource(int t, int level) {
        return null;
    }

    @Override
    public RealRandomAccessible<UnsignedShortType> getInterpolatedSource(int t, int level, Interpolation method) {
        return null;
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {

    }

    @Override
    public UnsignedShortType getType() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return null;
    }

    @Override
    public int getNumMipmapLevels() {
        return 0;
    }
}
