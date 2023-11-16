package bdv.util.source.alpha;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

/**
 *  Alpha Source for a source which is backed by a RAI and an AffineTransform
 *  This source has an alpha value which tends to zero on its surface
 */

public class AlphaSourceDistanceL1RAI extends AlphaSource { // TODO: check if extends AlphaSourceRAI wouldn't be better

    public AlphaSourceDistanceL1RAI(Source<?> origin, float sizePixX, float sizePixY, float sizePixZ) {
        super(origin);
        this.sizePixX = sizePixX;
        this.sizePixY = sizePixY;
        this.sizePixZ = sizePixZ;
    }

    final float sizePixX, sizePixY, sizePixZ;

    @Override
    public RandomAccessibleInterval<FloatType> getSource(int t, int level) {
        final long sX = origin.getSource(t,level).max(0);
        final long sY = origin.getSource(t,level).max(1);
        final long sZ = origin.getSource(t,level).max(2);
        final RandomAccessible< FloatType > randomAccessible;
        if (sZ==1) {
            randomAccessible =
                    new FunctionRandomAccessible<>( 3, () -> (loc, out) -> {
                        long x = loc.getLongPosition(0);
                        long y = loc.getLongPosition(1);
                        float dx = sizePixX*(0.5f+Math.min(x,sX-x));
                        float dy = sizePixY*(0.5f+Math.min(y,sY-y));
                        out.set( Math.min(dx,dy) );
                    }, FloatType::new );
        } else if (sX==1) {
            randomAccessible =
                    new FunctionRandomAccessible<>( 3, () -> (loc, out) -> {
                        long z = loc.getLongPosition(2);
                        long y = loc.getLongPosition(1);
                        float dz = sizePixX*(0.5f+Math.min(z,sZ-z));
                        float dy = sizePixY*(0.5f+Math.min(y,sY-y));
                        out.set( Math.min(dz,dy) );
                    }, FloatType::new );
        } else if (sY==1) {
            randomAccessible =
                    new FunctionRandomAccessible<>( 3, () -> (loc, out) -> {
                        long z = loc.getLongPosition(2);
                        long x = loc.getLongPosition(0);
                        float dz = sizePixX*(0.5f+Math.min(z,sZ-z));
                        float dx = sizePixY*(0.5f+Math.min(x,sX-x));
                        out.set( Math.min(dz,dx) );
                    }, FloatType::new );
        } else {
            randomAccessible =
                new FunctionRandomAccessible<>( 3, () -> (loc, out) -> {
                    long x = loc.getLongPosition(0);
                    long y = loc.getLongPosition(1);
                    long z = loc.getLongPosition(2);
                    float dx = sizePixX*(0.5f+Math.min(x,sX-x));
                    float dy = sizePixY*(0.5f+Math.min(y,sY-y));
                    float dz = sizePixZ*(0.5f+Math.min(z,sZ-z));
                    out.set( Math.min(Math.min(dx,dy),dz) );
                }, FloatType::new );
        }

        return Views.interval(randomAccessible, origin.getSource(t, level));
    }

    @Override
    public RealRandomAccessible<FloatType> getInterpolatedSource(int t, int level, Interpolation method) {
        ExtendedRandomAccessibleInterval<FloatType, RandomAccessibleInterval< FloatType >>
                eView = Views.extendZero(getSource( t, level ));
        return Views.interpolate( eView, interpolators.get(Interpolation.NEARESTNEIGHBOR) );
    }

    @Override
    public boolean intersectBox(AffineTransform3D affineTransform, Interval cell, int timepoint) {
        // Let's try a simplebox computation and see if there are intersections.
        Box3D box_cell = new Box3D(affineTransform, cell);
        AffineTransform3D affineTransform3D = new AffineTransform3D();
        getSourceTransform(timepoint,0,affineTransform3D);
        Box3D box_this = new Box3D(affineTransform3D, this.getSource(timepoint,0));
        return box_this.intersects(box_cell);
    }

    public static class Box3D {

        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        public Box3D(AffineTransform3D affineTransform, Interval cell) {
            // pXYZ
            RealPoint p000 = new RealPoint(cell.min(0)-1, cell.min(1)-1, cell.min(2)-1);
            RealPoint p001 = new RealPoint(cell.min(0)-1, cell.min(1)-1, cell.max(2)+1);
            RealPoint p010 = new RealPoint(cell.min(0)-1, cell.max(1)+1, cell.min(2)-1);
            RealPoint p011 = new RealPoint(cell.min(0)-1, cell.max(1)+1, cell.max(2)+1);
            RealPoint p100 = new RealPoint(cell.max(0)+1, cell.min(1)-1, cell.min(2)-1);
            RealPoint p101 = new RealPoint(cell.max(0)+1, cell.min(1)-1, cell.max(2)+1);
            RealPoint p110 = new RealPoint(cell.max(0)+1, cell.max(1)+1, cell.min(2)-1);
            RealPoint p111 = new RealPoint(cell.max(0)+1, cell.max(1)+1, cell.max(2)+1);

            affineTransform.apply(p000,p000);
            affineTransform.apply(p001,p001);
            affineTransform.apply(p010,p010);
            affineTransform.apply(p011,p011);
            affineTransform.apply(p100,p100);
            affineTransform.apply(p101,p101);
            affineTransform.apply(p110,p110);
            affineTransform.apply(p111,p111);

            updatePoint(p000);
            updatePoint(p001);
            updatePoint(p010);
            updatePoint(p011);
            updatePoint(p100);
            updatePoint(p101);
            updatePoint(p110);
            updatePoint(p111);
        }

        private void updatePoint(RealPoint pt) {
            double px = pt.getDoublePosition(0);
            double py = pt.getDoublePosition(1);
            double pz = pt.getDoublePosition(2);
            if (px<minX) minX = px;
            if (px>maxX) maxX = px;
            if (py<minY) minY = py;
            if (py>maxY) maxY = py;
            if (pz<minZ) minZ = pz;
            if (pz>maxZ) maxZ = pz;
        }

        public boolean intersects(Box3D other) {
            if (other.maxX<minX) return false;
            if (other.minX>maxX) return false;
            if (other.maxY<minY) return false;
            if (other.minY>maxY) return false;
            if (other.maxZ<minZ) return false;
            if (other.minZ>maxZ) return false;
            return true;
        }

        public String toString() {
            return "("+minX+":"+maxX+")"+"("+minY+":"+maxY+")"+"("+minZ+":"+maxZ+")";
        }
    }

}
